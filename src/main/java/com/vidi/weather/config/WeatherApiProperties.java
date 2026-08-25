package com.vidi.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weather")
public record WeatherApiProperties(
        OpenMeteo openMeteo,
        OpenWeatherMap openWeatherMap,
        Cache cache,
        Http http
) {
    // apiKey is blank by default (the free, IP-rate-limited anonymous endpoints) -- setting it
    // switches to Open-Meteo's customer-api.open-meteo.com domain (see application.yml), which
    // has its own dedicated daily quota instead of one shared with every other app on the same
    // egress IP (this is what actually exhausted the anonymous quota under normal use on Render).
    public record OpenMeteo(String geocodingUrl, String forecastUrl, String marineUrl, String apiKey) {
    }

    public record OpenWeatherMap(String baseUrl, String reverseGeocodingUrl, String apiKey) {
    }

    public record Cache(int ttlMinutes, long maxSize) {
    }

    public record Http(int connectTimeoutMs, int readTimeoutMs) {
    }
}
