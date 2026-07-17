package com.vidi.weather.model;

public record ForecastResult(ForecastData data, boolean fromCache) {
}
