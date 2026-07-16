package com.vidi.weather.service;

import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.provider.WeatherProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Wraps a single provider call with a circuit breaker (fails fast while a provider is
 * known to be unhealthy) and a retry with exponential backoff (absorbs transient errors),
 * both configured per provider name in application.yml.
 */
@Component
public class ProviderResilienceExecutor {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public ProviderResilienceExecutor(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    public WeatherData call(WeatherProvider provider, String city, Units units) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(provider.getProviderName());
        Retry retry = retryRegistry.retry(provider.getProviderName());

        Supplier<WeatherData> withRetry = Retry.decorateSupplier(retry, () -> provider.fetchCurrentWeather(city, units));
        Supplier<WeatherData> withCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, withRetry);

        return withCircuitBreaker.get();
    }
}
