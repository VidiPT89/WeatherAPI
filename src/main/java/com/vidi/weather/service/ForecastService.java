package com.vidi.weather.service;

import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.WeatherServiceException;
import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.ForecastResult;
import com.vidi.weather.model.Units;
import com.vidi.weather.provider.OpenMeteoProvider;
import com.vidi.weather.provider.OpenWeatherMapProvider;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ForecastService {

    private final OpenMeteoProvider openMeteoProvider;
    private final OpenWeatherMapProvider openWeatherMapProvider;
    private final ForecastCacheService cacheService;
    private final ProviderResilienceExecutor resilienceExecutor;

    public ForecastService(
            OpenMeteoProvider openMeteoProvider,
            OpenWeatherMapProvider openWeatherMapProvider,
            ForecastCacheService cacheService,
            ProviderResilienceExecutor resilienceExecutor) {
        this.openMeteoProvider = openMeteoProvider;
        this.openWeatherMapProvider = openWeatherMapProvider;
        this.cacheService = cacheService;
        this.resilienceExecutor = resilienceExecutor;
    }

    public ForecastResult getForecast(String city, Units units) {
        Optional<ForecastData> cached = cacheService.get(city, units);
        if (cached.isPresent()) {
            return new ForecastResult(cached.get(), true);
        }

        ForecastData fresh = fetchWithFallback(city, units);
        cacheService.put(city, units, fresh);
        return new ForecastResult(fresh, false);
    }

    /**
     * Open-Meteo is the everyday primary (richer forecast data), with OpenWeatherMap's coarser
     * 5-day/3-hour endpoint as a fallback -- unlike the current-weather aggregator, Open-Meteo
     * stays primary here rather than being demoted, since its forecast is genuinely better when
     * available; OpenWeatherMap only steps in when Open-Meteo itself is down (e.g. its shared-IP
     * quota on Render, see ADR-001), which has no bearing on Open-Meteo's own data quality.
     */
    private ForecastData fetchWithFallback(String city, Units units) {
        try {
            return resilienceExecutor.execute(
                    openMeteoProvider.getProviderName(), () -> openMeteoProvider.fetchForecast(city, units));
        } catch (CityNotFoundException ex) {
            throw ex;
        } catch (WeatherServiceException | CallNotPermittedException ex) {
            return resilienceExecutor.execute(
                    openWeatherMapProvider.getProviderName(), () -> openWeatherMapProvider.fetchForecast(city, units));
        }
    }
}
