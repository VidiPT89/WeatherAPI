package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.provider.WeatherProvider;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherAggregatorTest {

    @Mock
    private WeatherProvider weatherProvider;

    @Mock
    private CacheService cacheService;

    private WeatherAggregatorService aggregatorService;

    private final WeatherData sampleData = new WeatherData(
            "Lisboa", "Portugal", 22.5, 21.8, 65, 12.3, "Clear sky", Units.METRIC, "open-meteo", Instant.now());

    @Test
    void returnsCachedWeather_withoutCallingProvider_whenCacheHasEntry() {
        aggregatorService = new WeatherAggregatorService(weatherProvider, cacheService);
        when(cacheService.get("Lisboa", Units.METRIC)).thenReturn(Optional.of(sampleData));

        WeatherResult result = aggregatorService.getCurrentWeather("Lisboa", Units.METRIC);

        assertThat(result.fromCache()).isTrue();
        assertThat(result.data()).isEqualTo(sampleData);
        verify(weatherProvider, never()).fetchCurrentWeather(any(), any());
    }

    @Test
    void fetchesFromProviderAndCachesResult_whenCacheIsEmpty() {
        aggregatorService = new WeatherAggregatorService(weatherProvider, cacheService);
        when(cacheService.get("Lisboa", Units.METRIC)).thenReturn(Optional.empty());
        when(weatherProvider.fetchCurrentWeather("Lisboa", Units.METRIC)).thenReturn(sampleData);

        WeatherResult result = aggregatorService.getCurrentWeather("Lisboa", Units.METRIC);

        assertThat(result.fromCache()).isFalse();
        assertThat(result.data()).isEqualTo(sampleData);
        verify(cacheService).put("Lisboa", Units.METRIC, sampleData);
    }
}
