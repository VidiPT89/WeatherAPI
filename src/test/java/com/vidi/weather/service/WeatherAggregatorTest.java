package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vidi.weather.dto.CompareResponse;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.provider.WeatherProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherAggregatorTest {

    @Mock
    private WeatherProvider primaryProvider;

    @Mock
    private WeatherProvider secondaryProvider;

    @Mock
    private CacheService cacheService;

    @Mock
    private ProviderResilienceExecutor resilienceExecutor;

    private WeatherAggregatorService aggregatorService;

    private final WeatherData sampleData = new WeatherData(
            "Lisboa", "Portugal", 22.5, 21.8, 65, 12.3, "Clear sky", Units.METRIC, "open-meteo", Instant.now());

    @BeforeEach
    void setUp() {
        aggregatorService = new WeatherAggregatorService(
                List.of(primaryProvider, secondaryProvider), cacheService, resilienceExecutor);
    }

    @Test
    void returnsCachedWeather_withoutCallingAnyProvider_whenCacheHasEntry() {
        when(cacheService.get("Lisboa", Units.METRIC)).thenReturn(Optional.of(sampleData));

        WeatherResult result = aggregatorService.getCurrentWeather("Lisboa", Units.METRIC);

        assertThat(result.fromCache()).isTrue();
        assertThat(result.data()).isEqualTo(sampleData);
        verifyNoInteractions(resilienceExecutor);
    }

    @Test
    void fetchesFromPrimaryProviderAndCachesResult_whenCacheIsEmpty() {
        when(cacheService.get("Lisboa", Units.METRIC)).thenReturn(Optional.empty());
        when(resilienceExecutor.call(primaryProvider, "Lisboa", Units.METRIC)).thenReturn(sampleData);

        WeatherResult result = aggregatorService.getCurrentWeather("Lisboa", Units.METRIC);

        assertThat(result.fromCache()).isFalse();
        assertThat(result.data()).isEqualTo(sampleData);
        verify(cacheService).put("Lisboa", Units.METRIC, sampleData);
        verify(resilienceExecutor, never()).call(eq(secondaryProvider), any(), any());
    }

    @Test
    void fallsBackToSecondaryProvider_whenPrimaryIsUnavailable() {
        when(cacheService.get("Lisboa", Units.METRIC)).thenReturn(Optional.empty());
        when(resilienceExecutor.call(primaryProvider, "Lisboa", Units.METRIC))
                .thenThrow(new ProviderUnavailableException("open-meteo", new RuntimeException("down")));
        when(resilienceExecutor.call(secondaryProvider, "Lisboa", Units.METRIC)).thenReturn(sampleData);

        WeatherResult result = aggregatorService.getCurrentWeather("Lisboa", Units.METRIC);

        assertThat(result.data()).isEqualTo(sampleData);
    }

    @Test
    void propagatesLastFailure_whenAllProvidersAreUnavailable() {
        when(cacheService.get(any(), any())).thenReturn(Optional.empty());
        ProviderUnavailableException secondaryFailure =
                new ProviderUnavailableException("open-weather-map", new RuntimeException("down"));
        when(resilienceExecutor.call(primaryProvider, "Lisboa", Units.METRIC))
                .thenThrow(new ProviderUnavailableException("open-meteo", new RuntimeException("down")));
        when(resilienceExecutor.call(secondaryProvider, "Lisboa", Units.METRIC)).thenThrow(secondaryFailure);

        assertThatThrownBy(() -> aggregatorService.getCurrentWeather("Lisboa", Units.METRIC))
                .isSameAs(secondaryFailure);
    }

    @Test
    void doesNotFallBack_whenCityIsNotFound() {
        when(cacheService.get(any(), any())).thenReturn(Optional.empty());
        when(resilienceExecutor.call(primaryProvider, "Atlantis", Units.METRIC))
                .thenThrow(new CityNotFoundException("Atlantis"));

        assertThatThrownBy(() -> aggregatorService.getCurrentWeather("Atlantis", Units.METRIC))
                .isInstanceOf(CityNotFoundException.class);
        verify(resilienceExecutor, never()).call(eq(secondaryProvider), any(), any());
    }

    @Test
    void compareProvidersReturnsResultsFromEachProviderIndependently() {
        when(secondaryProvider.getProviderName()).thenReturn("open-weather-map");
        when(resilienceExecutor.call(primaryProvider, "Lisboa", Units.METRIC)).thenReturn(sampleData);
        when(resilienceExecutor.call(secondaryProvider, "Lisboa", Units.METRIC))
                .thenThrow(new ProviderUnavailableException("open-weather-map", new RuntimeException("down")));

        CompareResponse response = aggregatorService.compareProviders("Lisboa", Units.METRIC);

        assertThat(response.city()).isEqualTo("Lisboa");
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).success()).isTrue();
        assertThat(response.results().get(1).success()).isFalse();
        assertThat(response.results().get(1).provider()).isEqualTo("open-weather-map");
        assertThat(response.results().get(1).errorMessage()).isEqualTo("Provider unavailable");
    }

    @Test
    void compareProvidersReportsCityNotFound_asAPerProviderFailure() {
        when(secondaryProvider.getProviderName()).thenReturn("open-weather-map");
        when(resilienceExecutor.call(primaryProvider, "Atlantis", Units.METRIC)).thenReturn(sampleData);
        when(resilienceExecutor.call(secondaryProvider, "Atlantis", Units.METRIC))
                .thenThrow(new CityNotFoundException("Atlantis"));

        CompareResponse response = aggregatorService.compareProviders("Atlantis", Units.METRIC);

        assertThat(response.results().get(1).success()).isFalse();
        assertThat(response.results().get(1).errorMessage()).isEqualTo("City not found");
    }
}
