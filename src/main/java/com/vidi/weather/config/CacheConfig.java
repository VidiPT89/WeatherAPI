package com.vidi.weather.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vidi.weather.model.WeatherData;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, WeatherData> weatherCache(WeatherApiProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.cache().ttlMinutes()))
                .maximumSize(properties.cache().maxSize())
                .recordStats()
                .build();
    }
}
