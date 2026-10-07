package com.vidi.weather.service;

import com.vidi.weather.model.MarineData;
import com.vidi.weather.model.MarineResult;
import com.vidi.weather.model.Units;
import com.vidi.weather.provider.OpenMeteoProvider;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MarineService {

    private final OpenMeteoProvider openMeteoProvider;
    private final MarineCacheService cacheService;
    private final ProviderResilienceExecutor resilienceExecutor;

    public MarineService(
            OpenMeteoProvider openMeteoProvider,
            MarineCacheService cacheService,
            ProviderResilienceExecutor resilienceExecutor) {
        this.openMeteoProvider = openMeteoProvider;
        this.cacheService = cacheService;
        this.resilienceExecutor = resilienceExecutor;
    }

    public MarineResult getMarineConditions(String city, Units units) {
        Optional<MarineData> cached = cacheService.get(city, units);
        if (cached.isPresent()) {
            return new MarineResult(cached.get(), true);
        }

        MarineData fresh = resilienceExecutor.execute(
                openMeteoProvider.getProviderName(), () -> openMeteoProvider.fetchMarineConditions(city, units));
        cacheService.put(city, units, fresh);
        return new MarineResult(fresh, false);
    }

    /** Sea conditions for exact coordinates; see {@link ForecastService#getForecastAt}. */
    public MarineResult getMarineConditionsAt(GeocodingResult location, Units units) {
        String cacheKey = ForecastService.coordinateKey(location);
        Optional<MarineData> cached = cacheService.get(cacheKey, units);
        if (cached.isPresent()) {
            return new MarineResult(cached.get(), true);
        }

        MarineData fresh = resilienceExecutor.execute(
                openMeteoProvider.getProviderName(), () -> openMeteoProvider.fetchMarineConditionsAt(location, units));
        cacheService.put(cacheKey, units, fresh);
        return new MarineResult(fresh, false);
    }
}
