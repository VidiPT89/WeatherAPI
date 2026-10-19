package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.vidi.weather.model.CoastOutlookResult;
import com.vidi.weather.model.CoastSpot;
import com.vidi.weather.provider.CoastOutlookClient;
import com.vidi.weather.provider.openmeteo.CoastMarineResponse;
import com.vidi.weather.provider.openmeteo.CoastWindResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoastOutlookServiceTest {

    @Mock
    private CoastOutlookClient client;

    @Mock
    private ProviderResilienceExecutor resilienceExecutor;

    private CoastOutlookService service;

    private final CoastSpot spot = new CoastSpot("guincho", "Guincho", "Cascais", 38.731, -9.476, 275);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(resilienceExecutor.execute(eq("open-meteo"), any())).thenAnswer(
                invocation -> ((Supplier<Object>) invocation.getArgument(1)).get());
        service = new CoastOutlookService(client, resilienceExecutor, Caffeine.newBuilder().build(),
                Clock.fixed(Instant.parse("2026-10-20T09:30:00Z"), ZoneOffset.UTC));
    }

    @Test
    void fetchesOnce_thenServesTheSpotFromCache() {
        when(client.fetchSea(38.731, -9.476)).thenReturn(new CoastMarineResponse(new CoastMarineResponse.Hourly(
                List.of("2026-10-20T10:00"), List.of(1.2), List.of(11.0), List.of(290.0),
                List.of(1.0), List.of(12.0), List.of(290.0), List.of(17.0), List.of(0.5))));
        when(client.fetchWind(38.731, -9.476)).thenReturn(new CoastWindResponse(
                new CoastWindResponse.Hourly(List.of("2026-10-20T10:00"), List.of(8.0), List.of(90.0),
                        List.of(14.0), List.of(19.0), List.of(0)),
                new CoastWindResponse.Daily(List.of("2026-10-20"), List.of("2026-10-20T07:40"),
                        List.of("2026-10-20T18:50")),
                3600));

        CoastOutlookResult first = service.getOutlook(spot);
        CoastOutlookResult second = service.getOutlook(spot);

        assertThat(first.fromCache()).isFalse();
        assertThat(second.fromCache()).isTrue();
        assertThat(first.data().hours()).hasSize(1);
        // 09:30 UTC is 10:30 at the spot (UTC+1), so the 10:00 hour still counts for today's window.
        assertThat(first.data().days().get(0).surf()).isNotNull();
        verify(client, times(1)).fetchSea(38.731, -9.476);
        verify(client, times(1)).fetchWind(38.731, -9.476);
    }
}
