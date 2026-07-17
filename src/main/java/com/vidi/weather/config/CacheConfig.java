package com.vidi.weather.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, WeatherData> weatherCache(WeatherApiProperties properties) {
        return buildCache(properties);
    }

    @Bean
    public Cache<String, ForecastData> forecastCache(WeatherApiProperties properties) {
        return buildCache(properties);
    }

    @Bean
    public Cache<String, List<GeocodingResult>> geocodingCache(WeatherApiProperties properties) {
        return buildCache(properties);
    }

    private <T> Cache<String, T> buildCache(WeatherApiProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.cache().ttlMinutes()))
                .maximumSize(properties.cache().maxSize())
                .recordStats()
                .build();
    }
}
