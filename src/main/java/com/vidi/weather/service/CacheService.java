package com.vidi.weather.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

    private final Cache<String, WeatherData> cache;

    public CacheService(Cache<String, WeatherData> weatherCache) {
        this.cache = weatherCache;
    }

    public Optional<WeatherData> get(String city, Units units) {
        return Optional.ofNullable(cache.getIfPresent(buildKey(city, units)));
    }

    public void put(String city, Units units, WeatherData data) {
        cache.put(buildKey(city, units), data);
    }

    private String buildKey(String city, Units units) {
        return "%s:%s".formatted(city.trim().toLowerCase(), units.name());
    }
}
