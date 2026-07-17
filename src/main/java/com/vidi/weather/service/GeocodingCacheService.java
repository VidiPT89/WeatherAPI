package com.vidi.weather.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GeocodingCacheService {

    private final Cache<String, List<GeocodingResult>> cache;

    public GeocodingCacheService(Cache<String, List<GeocodingResult>> geocodingCache) {
        this.cache = geocodingCache;
    }

    public Optional<List<GeocodingResult>> get(String query, int limit) {
        return Optional.ofNullable(cache.getIfPresent(buildKey(query, limit)));
    }

    public void put(String query, int limit, List<GeocodingResult> results) {
        cache.put(buildKey(query, limit), results);
    }

    private String buildKey(String query, int limit) {
        return "%s:%d".formatted(query.trim().toLowerCase(), limit);
    }
}
