package com.vidi.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weather")
public record WeatherApiProperties(
        OpenMeteo openMeteo,
        OpenWeatherMap openWeatherMap,
        Cache cache,
        Http http
) {
    public record OpenMeteo(String geocodingUrl, String forecastUrl) {
    }

    public record OpenWeatherMap(String baseUrl, String apiKey) {
    }

    public record Cache(int ttlMinutes, long maxSize) {
    }

    public record Http(int connectTimeoutMs, int readTimeoutMs) {
    }
}
