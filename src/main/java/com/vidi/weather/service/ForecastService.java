package com.vidi.weather.service;

import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.WeatherServiceException;
import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.ForecastResult;
import com.vidi.weather.model.Units;
import com.vidi.weather.provider.OpenMeteoProvider;
import com.vidi.weather.provider.OpenWeatherMapProvider;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
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
     * Forecast for exact coordinates (the "use my location" flow). Reverse geocoding can name a
     * parish ("São Sebastião da Pedreira") that the by-name lookup then fails to find, so this
     * path never goes back through a name.
     */
    public ForecastResult getForecastAt(GeocodingResult location, Units units) {
        String cacheKey = coordinateKey(location);
        Optional<ForecastData> cached = cacheService.get(cacheKey, units);
        if (cached.isPresent()) {
            return new ForecastResult(cached.get(), true);
        }

        ForecastData fresh;
        try {
            fresh = resilienceExecutor.execute(
                    openMeteoProvider.getProviderName(), () -> openMeteoProvider.fetchForecastAt(location, units));
        } catch (WeatherServiceException | CallNotPermittedException ex) {
            fresh = resilienceExecutor.execute(
                    openWeatherMapProvider.getProviderName(),
                    () -> openWeatherMapProvider.fetchForecastByCoordinates(
                            location.latitude(), location.longitude(), location.name(), units));
        }
        cacheService.put(cacheKey, units, fresh);
        return new ForecastResult(fresh, false);
    }

    /** Cache key for a coordinate lookup; ~100 m precision, and never collides with a city name. */
    static String coordinateKey(GeocodingResult location) {
        return "@%.3f,%.3f".formatted(location.latitude(), location.longitude());
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
