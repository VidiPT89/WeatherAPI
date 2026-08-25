package com.vidi.weather.controller;

import com.vidi.weather.config.WeatherApiProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Temporary: reports the raw outcome of calling each weather provider directly from wherever
 * this backend is actually deployed (Render), bypassing the circuit breaker/cache/fallback
 * chain -- exists only to see the real connect/read exception behind repeated Open-Meteo
 * fallbacks, since Render doesn't expose logs to this session. Delete once diagnosed.
 */
@RestController
@RequestMapping("/api/v1/diagnostics")
public class DiagnosticsController {

    private final RestTemplate restTemplate;
    private final WeatherApiProperties properties;

    public DiagnosticsController(RestTemplate weatherRestTemplate, WeatherApiProperties properties) {
        this.restTemplate = weatherRestTemplate;
        this.properties = properties;
    }

    public record ProviderCheck(String provider, boolean ok, long millis, String detail) {
    }

    @GetMapping("/providers")
    public java.util.List<ProviderCheck> checkProviders() {
        return java.util.List.of(check("open-meteo", this::pingOpenMeteo), check("open-weather-map", this::pingOpenWeatherMap));
    }

    private ProviderCheck check(String name, Runnable ping) {
        Instant start = Instant.now();
        try {
            ping.run();
            return new ProviderCheck(name, true, Duration.between(start, Instant.now()).toMillis(), "ok");
        } catch (Exception ex) {
            String detail = ex.getClass().getName() + ": " + ex.getMessage()
                    + (ex.getCause() != null ? " | cause: " + ex.getCause().getClass().getName() + ": " + ex.getCause().getMessage() : "");
            return new ProviderCheck(name, false, Duration.between(start, Instant.now()).toMillis(), detail);
        }
    }

    private void pingOpenMeteo() {
        String apiKey = properties.openMeteo().apiKey();
        String uri = properties.openMeteo().forecastUrl()
                + "?latitude=38.72&longitude=-9.13&current=temperature_2m"
                + (apiKey != null && !apiKey.isBlank() ? "&apikey=" + apiKey : "");
        restTemplate.getForObject(uri, String.class);
    }

    private void pingOpenWeatherMap() {
        String uri = properties.openWeatherMap().baseUrl()
                + "?q=Lisbon&appid=" + properties.openWeatherMap().apiKey();
        restTemplate.getForObject(uri, String.class);
    }
}
