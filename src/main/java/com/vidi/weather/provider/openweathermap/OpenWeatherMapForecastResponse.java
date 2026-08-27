package com.vidi.weather.provider.openweathermap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * OpenWeatherMap's free "5 day / 3 hour" forecast endpoint. Coarser than Open-Meteo's (3h steps,
 * 5 days ahead, no UV index or sunrise/sunset per day) -- used only as a fallback when Open-Meteo
 * itself is unavailable, not as the everyday primary (see {@code ForecastService}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherMapForecastResponse(List<Entry> list, City city) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            long dt,
            Main main,
            List<OpenWeatherMapResponse.WeatherDescription> weather,
            Wind wind,
            double pop
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
            double temp,
            @com.fasterxml.jackson.annotation.JsonProperty("temp_min") double tempMin,
            @com.fasterxml.jackson.annotation.JsonProperty("temp_max") double tempMax
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(double speed) {
    }

    /**
     * {@code timezone} is the UTC offset in seconds, needed to turn each entry's UTC {@code dt}
     * into local time. {@code sunrise}/{@code sunset} are today's only (unlike Open-Meteo, this
     * endpoint doesn't give a per-day value for the whole forecast window) -- reused as an
     * approximation for every day in the fallback forecast, which drifts by at most a few minutes
     * across the 5-day window this endpoint covers.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record City(String country, int timezone, long sunrise, long sunset) {
    }
}
