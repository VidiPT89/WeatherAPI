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
}
