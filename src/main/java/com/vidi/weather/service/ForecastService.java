package com.vidi.weather.service;

import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.ForecastResult;
import com.vidi.weather.model.Units;
import com.vidi.weather.provider.OpenMeteoProvider;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ForecastService {

    private final OpenMeteoProvider openMeteoProvider;
    private final ForecastCacheService cacheService;
    private final ProviderResilienceExecutor resilienceExecutor;

    public ForecastService(
            OpenMeteoProvider openMeteoProvider,
            ForecastCacheService cacheService,
            ProviderResilienceExecutor resilienceExecutor) {
        this.openMeteoProvider = openMeteoProvider;
        this.cacheService = cacheService;
        this.resilienceExecutor = resilienceExecutor;
    }

    public ForecastResult getForecast(String city, Units units) {
        Optional<ForecastData> cached = cacheService.get(city, units);
        if (cached.isPresent()) {
            return new ForecastResult(cached.get(), true);
        }

        ForecastData fresh = resilienceExecutor.execute(
                openMeteoProvider.getProviderName(), () -> openMeteoProvider.fetchForecast(city, units));
        cacheService.put(city, units, fresh);
        return new ForecastResult(fresh, false);
    }
}
