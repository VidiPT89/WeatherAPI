package com.vidi.weather.provider.openweathermap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherMapReverseGeocodingEntry(String name, String country, double lat, double lon) {
}
