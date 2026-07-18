package com.vidi.weather.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.vidi.weather.model.MarineData;
import com.vidi.weather.model.Units;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MarineCacheService {

    private final Cache<String, MarineData> cache;

    public MarineCacheService(Cache<String, MarineData> marineCache) {
        this.cache = marineCache;
    }

    public Optional<MarineData> get(String city, Units units) {
        return Optional.ofNullable(cache.getIfPresent(buildKey(city, units)));
    }

    public void put(String city, Units units, MarineData data) {
        cache.put(buildKey(city, units), data);
    }

    private String buildKey(String city, Units units) {
        return "%s:%s".formatted(city.trim().toLowerCase(), units.name());
    }
}
