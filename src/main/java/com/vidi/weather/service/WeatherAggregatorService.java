package com.vidi.weather.service;

import com.vidi.weather.dto.CompareResponse;
import com.vidi.weather.dto.ProviderComparisonEntry;
import com.vidi.weather.dto.WeatherResponse;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
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

    /**
     * Queries every configured provider independently for the same city, so callers can see
     * how sources compare side by side. A failing provider yields an error entry instead of
     * failing the whole request, and never exposes the provider's raw error message.
     */
    public CompareResponse compareProviders(String city, Units units) {
        List<ProviderComparisonEntry> entries = providers.stream()
                .map(provider -> compareOne(provider, city, units))
                .toList();
        return new CompareResponse(city, entries);
    }

    private ProviderComparisonEntry compareOne(WeatherProvider provider, String city, Units units) {
        try {
            WeatherData data = resilienceExecutor.call(provider, city, units);
            return ProviderComparisonEntry.success(WeatherResponse.from(new WeatherResult(data, false)));
        } catch (CityNotFoundException ex) {
            return ProviderComparisonEntry.failure(provider.getProviderName(), "City not found");
        } catch (ProviderQuotaExceededException ex) {
            return ProviderComparisonEntry.failure(provider.getProviderName(), "Provider quota exceeded");
        } catch (WeatherServiceException | CallNotPermittedException ex) {
            return ProviderComparisonEntry.failure(provider.getProviderName(), "Provider unavailable");
        }
    }
}
