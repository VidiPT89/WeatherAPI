package com.vidi.weather.model;

public record WeatherResult(WeatherData data, boolean fromCache) {
}
