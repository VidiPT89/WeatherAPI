package com.vidi.weather.provider.openmeteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForecastResponse(CurrentWeather current) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentWeather(
            @JsonProperty("temperature_2m") double temperature,
            @JsonProperty("relative_humidity_2m") int humidity,
            @JsonProperty("apparent_temperature") double feelsLike,
            @JsonProperty("wind_speed_10m") double windSpeed,
            @JsonProperty("weather_code") int weatherCode
    ) {
    }
}
