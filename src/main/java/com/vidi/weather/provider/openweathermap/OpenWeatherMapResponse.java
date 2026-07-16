package com.vidi.weather.provider.openweathermap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherMapResponse(
        List<WeatherDescription> weather,
        Main main,
        Wind wind,
        String name,
        Sys sys
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherDescription(String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
            double temp,
            @JsonProperty("feels_like") double feelsLike,
            int humidity
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(double speed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sys(String country) {
    }
}
