package com.vidi.weather.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate weatherRestTemplate(RestTemplateBuilder builder, WeatherApiProperties properties) {
        return builder
                .connectTimeout(Duration.ofMillis(properties.http().connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.http().readTimeoutMs()))
                .build();
    }
}
