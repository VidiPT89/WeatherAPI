package com.vidi.weather.service;

import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.exception.WeatherServiceException;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.provider.WeatherProvider;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class WeatherAggregatorService {

    private final List<WeatherProvider> providers;
    private final CacheService cacheService;
    private final ProviderResilienceExecutor resilienceExecutor;

    public WeatherAggregatorService(
            List<WeatherProvider> providers, CacheService cacheService, ProviderResilienceExecutor resilienceExecutor) {
        this.providers = providers;
        this.cacheService = cacheService;
        this.resilienceExecutor = resilienceExecutor;
    }

    public WeatherResult getCurrentWeather(String city, Units units) {
        Optional<WeatherData> cached = cacheService.get(city, units);
        if (cached.isPresent()) {
            return new WeatherResult(cached.get(), true);
        }

        WeatherData fresh = fetchWithFallback(city, units);
        cacheService.put(city, units, fresh);
        return new WeatherResult(fresh, false);
    }

    /**
     * Same as {@link #getCurrentWeather(String, Units)} but for GPS-based lookups: goes straight
     * to each provider's coordinates, instead of resolving a reverse-geocoded city name back to
     * coordinates through that provider's own name-based geocoding (which can fail to match the
     * exact name a *different* provider used for the reverse-geocoding step).
     */
    public WeatherResult getCurrentWeatherByCoordinates(double latitude, double longitude, String cityName, Units units) {
        Optional<WeatherData> cached = cacheService.get(cityName, units);
        if (cached.isPresent()) {
            return new WeatherResult(cached.get(), true);
        }

        WeatherData fresh = fetchWithFallbackByCoordinates(latitude, longitude, cityName, units);
        cacheService.put(cityName, units, fresh);
        return new WeatherResult(fresh, false);
    }

    /**
     * Tries each configured provider in order, falling back to the next one when a provider
     * is unavailable or its circuit breaker is open. A city genuinely not found is not a
     * provider fault, so it is propagated immediately instead of triggering a fallback.
     */
    private WeatherData fetchWithFallback(String city, Units units) {
        RuntimeException lastFailure = null;

        for (WeatherProvider provider : providers) {
            try {
                return resilienceExecutor.call(provider, city, units);
            } catch (CityNotFoundException ex) {
                throw ex;
            } catch (WeatherServiceException | CallNotPermittedException ex) {
                lastFailure = ex;
            }
        }

        throw lastFailure != null ? lastFailure : new ProviderUnavailableException("all-providers", null);
    }

    private WeatherData fetchWithFallbackByCoordinates(double latitude, double longitude, String cityName, Units units) {
        RuntimeException lastFailure = null;

        for (WeatherProvider provider : providers) {
            try {
                return resilienceExecutor.callByCoordinates(provider, latitude, longitude, cityName, units);
            } catch (CityNotFoundException ex) {
                throw ex;
            } catch (WeatherServiceException | CallNotPermittedException ex) {
                lastFailure = ex;
            }
        }

        throw lastFailure != null ? lastFailure : new ProviderUnavailableException("all-providers", null);
    }
}
