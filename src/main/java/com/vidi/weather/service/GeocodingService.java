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

    private static final int COUNTRY_HINT_CANDIDATE_COUNT = 10;

    /**
     * Resolves a "name, country" pair (the exact format the search-suggestion dropdown submits,
     * see the client's disambiguation comment for e.g. "Beja, Portugal" vs. "Beja, Tunisia") to
     * the one matching result, instead of a bare name search's single top match by relevance.
     * Needed because {@link com.vidi.weather.provider.OpenWeatherMapProvider}'s weather-by-name
     * endpoint only understands ISO country codes, not a full country name -- forwarding
     * "Beja, Portugal" to it verbatim silently falls back to matching "Beja" alone and can return
     * a same-named city in the wrong country instead of an error.
     */
    public Optional<GeocodingResult> resolveByNameAndCountry(String cityName, String countryHint) {
        return searchCities(cityName, COUNTRY_HINT_CANDIDATE_COUNT).stream()
                .filter(result -> result.country() != null && result.country().equalsIgnoreCase(countryHint))
                .findFirst();
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
