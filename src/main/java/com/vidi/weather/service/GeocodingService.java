package com.vidi.weather.service;

import com.vidi.weather.provider.OpenMeteoProvider;
import com.vidi.weather.provider.OpenWeatherMapProvider;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GeocodingService {

    private final OpenMeteoProvider openMeteoProvider;
    private final OpenWeatherMapProvider openWeatherMapProvider;
    private final GeocodingCacheService cacheService;
    private final ReverseGeocodingCacheService reverseGeocodingCacheService;
    private final ProviderResilienceExecutor resilienceExecutor;

    public GeocodingService(
            OpenMeteoProvider openMeteoProvider,
            OpenWeatherMapProvider openWeatherMapProvider,
            GeocodingCacheService cacheService,
            ReverseGeocodingCacheService reverseGeocodingCacheService,
            ProviderResilienceExecutor resilienceExecutor) {
        this.openMeteoProvider = openMeteoProvider;
        this.openWeatherMapProvider = openWeatherMapProvider;
        this.cacheService = cacheService;
        this.reverseGeocodingCacheService = reverseGeocodingCacheService;
        this.resilienceExecutor = resilienceExecutor;
    }

    public List<GeocodingResult> searchCities(String query, int limit) {
        Optional<List<GeocodingResult>> cached = cacheService.get(query, limit);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<GeocodingResult> results = resilienceExecutor.execute(
                openMeteoProvider.getProviderName(), () -> openMeteoProvider.searchCities(query, limit));
        cacheService.put(query, limit, results);
        return results;
    }

    /** Resolves GPS coordinates to a city, for the Dashboard's "use my location" feature. */
    public Optional<GeocodingResult> reverseGeocode(double latitude, double longitude) {
        Optional<GeocodingResult> cached = reverseGeocodingCacheService.get(latitude, longitude);
        if (cached.isPresent()) {
            return cached;
        }

        List<GeocodingResult> results = resilienceExecutor.execute(
                openWeatherMapProvider.getProviderName(),
                () -> openWeatherMapProvider.reverseGeocode(latitude, longitude));
        Optional<GeocodingResult> result = results.stream().findFirst();
        result.ifPresent(r -> reverseGeocodingCacheService.put(latitude, longitude, r));
        return result;
    }
}
