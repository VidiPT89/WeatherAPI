package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CacheServiceTest {

    private final WeatherData sampleData = new WeatherData(
            "Lisboa", "Portugal", 22.5, 21.8, 65, 12.3, "Clear sky", Units.METRIC, "open-meteo", Instant.now());

    @Test
    void returnsEmpty_whenNoEntryCachedForKey() {
        CacheService cacheService = new CacheService(Caffeine.newBuilder().build());

        assertThat(cacheService.get("Lisboa", Units.METRIC)).isEmpty();
    }

    @Test
    void returnsStoredValue_afterPut() {
        CacheService cacheService = new CacheService(Caffeine.newBuilder().build());

        cacheService.put("Lisboa", Units.METRIC, sampleData);

        assertThat(cacheService.get("Lisboa", Units.METRIC)).contains(sampleData);
    }

    @Test
    void treatsCityKeyCaseAndWhitespaceInsensitively() {
        CacheService cacheService = new CacheService(Caffeine.newBuilder().build());

        cacheService.put("Lisboa", Units.METRIC, sampleData);

        assertThat(cacheService.get("  LISBOA  ", Units.METRIC)).contains(sampleData);
    }

    @Test
    void keepsEntriesForDifferentUnitsSeparate() {
        CacheService cacheService = new CacheService(Caffeine.newBuilder().build());
        WeatherData imperialData = new WeatherData(
                "Lisboa", "Portugal", 72.5, 71.2, 65, 7.6, "Clear sky", Units.IMPERIAL, "open-meteo", Instant.now());

        cacheService.put("Lisboa", Units.METRIC, sampleData);
        cacheService.put("Lisboa", Units.IMPERIAL, imperialData);

        assertThat(cacheService.get("Lisboa", Units.METRIC)).contains(sampleData);
        assertThat(cacheService.get("Lisboa", Units.IMPERIAL)).contains(imperialData);
    }

    @Test
    void entryExpiresAfterConfiguredTtl() throws InterruptedException {
        CacheService cacheService = new CacheService(
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(50)).build());

        cacheService.put("Lisboa", Units.METRIC, sampleData);
        Thread.sleep(150);

        assertThat(cacheService.get("Lisboa", Units.METRIC)).isEmpty();
    }

    @Test
    void returnsStoredValue_afterPutByCoordinates() {
        CacheService cacheService = new CacheService(Caffeine.newBuilder().build());

        cacheService.putByCoordinates(38.7167, -9.1333, Units.METRIC, sampleData);

        assertThat(cacheService.getByCoordinates(38.7167, -9.1333, Units.METRIC)).contains(sampleData);
    }

    @Test
    void doesNotConfuseDistinctLocationsSharingTheSameCityName_whenKeyedByCoordinates() {
        // Real bug: two different real-world places can reverse-geocode to the same city name
        // (e.g. "Springfield" in different countries). GPS lookups must be cached by coordinates,
        // not by name, or one location's weather leaks into another's /nearby response.
        CacheService cacheService = new CacheService(Caffeine.newBuilder().build());
        WeatherData otherLocationSameName = new WeatherData(
                "Springfield", "United States", 5.0, 3.0, 80, 4.0, "Cloudy", Units.METRIC, "open-meteo", Instant.now());

        cacheService.putByCoordinates(39.7817, -89.6501, Units.METRIC, sampleData);

        assertThat(cacheService.getByCoordinates(37.2153, -93.2982, Units.METRIC)).isEmpty();

        cacheService.putByCoordinates(37.2153, -93.2982, Units.METRIC, otherLocationSameName);

        assertThat(cacheService.getByCoordinates(39.7817, -89.6501, Units.METRIC)).contains(sampleData);
        assertThat(cacheService.getByCoordinates(37.2153, -93.2982, Units.METRIC)).contains(otherLocationSameName);
    }
}
