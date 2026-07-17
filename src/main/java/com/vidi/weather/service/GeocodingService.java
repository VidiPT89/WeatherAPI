package com.vidi.weather.service;

import com.vidi.weather.provider.OpenMeteoProvider;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GeocodingService {

    private final OpenMeteoProvider openMeteoProvider;
    private final GeocodingCacheService cacheService;
    private final ProviderResilienceExecutor resilienceExecutor;

    public GeocodingService(
            OpenMeteoProvider openMeteoProvider,
            GeocodingCacheService cacheService,
            ProviderResilienceExecutor resilienceExecutor) {
        this.openMeteoProvider = openMeteoProvider;
        this.cacheService = cacheService;
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
}
