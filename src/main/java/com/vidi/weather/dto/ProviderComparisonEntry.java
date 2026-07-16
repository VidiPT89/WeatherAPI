package com.vidi.weather.dto;

public record ProviderComparisonEntry(String provider, boolean success, WeatherResponse weather, String errorMessage) {

    public static ProviderComparisonEntry success(WeatherResponse weather) {
        return new ProviderComparisonEntry(weather.provider(), true, weather, null);
    }

    public static ProviderComparisonEntry failure(String provider, String errorMessage) {
        return new ProviderComparisonEntry(provider, false, null, errorMessage);
    }
}
