package com.vidi.weather.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReverseGeocodingCacheService {

    private final Cache<String, GeocodingResult> cache;

    public ReverseGeocodingCacheService(Cache<String, GeocodingResult> reverseGeocodingCache) {
        this.cache = reverseGeocodingCache;
    }

    public Optional<GeocodingResult> get(double latitude, double longitude) {
        return Optional.ofNullable(cache.getIfPresent(buildKey(latitude, longitude)));
    }

    public void put(double latitude, double longitude, GeocodingResult result) {
        cache.put(buildKey(latitude, longitude), result);
    }

    /** Rounds to 2 decimal places (~1.1km) so nearby GPS pings share a cache entry. */
    private String buildKey(double latitude, double longitude) {
        return "%.2f:%.2f".formatted(latitude, longitude);
    }
}
