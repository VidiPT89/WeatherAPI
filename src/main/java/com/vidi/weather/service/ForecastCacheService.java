package com.vidi.weather.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.Units;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ForecastCacheService {

    private final Cache<String, ForecastData> cache;

    public ForecastCacheService(Cache<String, ForecastData> forecastCache) {
        this.cache = forecastCache;
    }

    public Optional<ForecastData> get(String city, Units units) {
        return Optional.ofNullable(cache.getIfPresent(buildKey(city, units)));
    }

    public void put(String city, Units units, ForecastData data) {
        cache.put(buildKey(city, units), data);
    }

    private String buildKey(String city, Units units) {
        return "%s:%s".formatted(city.trim().toLowerCase(), units.name());
    }
}
