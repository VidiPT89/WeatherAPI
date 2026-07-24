package com.vidi.weather.model;

public record WeatherInsightsData(
        String city,
        String country,
        MoonPhaseInfo moonPhase,
        String uvRiskLabel,
        int outdoorActivityScore,
        String outdoorActivityLabel,
        String fishingConditionLabel
) {
}
