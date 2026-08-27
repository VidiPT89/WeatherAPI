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

    /**
     * Coordinate-keyed variant for GPS-based lookups. A city name alone is not a safe cache
     * key there: distinct real-world locations can share the same reverse-geocoded name (e.g.
     * "Springfield" in different countries), which would otherwise let one location's cached
     * weather be served for another's GPS request. Rounded to ~1.1km so nearby pings still share
     * an entry, matching {@link ReverseGeocodingCacheService}.
     */
    public Optional<WeatherData> getByCoordinates(double latitude, double longitude, Units units) {
        return Optional.ofNullable(cache.getIfPresent(buildCoordinateKey(latitude, longitude, units)));
    }

    public void putByCoordinates(double latitude, double longitude, Units units, WeatherData data) {
        cache.put(buildCoordinateKey(latitude, longitude, units), data);
    }

    private String buildKey(String city, Units units) {
        return "%s:%s".formatted(city.trim().toLowerCase(), units.name());
    }

    private String buildCoordinateKey(double latitude, double longitude, Units units) {
        return "coord:%.2f:%.2f:%s".formatted(latitude, longitude, units.name());
    }
}
