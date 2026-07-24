package com.vidi.weather.dto;

import com.vidi.weather.model.MoonPhaseInfo;
import com.vidi.weather.model.WeatherInsightsData;

public record WeatherInsightsResponse(
        String city,
        String country,
        MoonPhaseInfo moonPhase,
        String uvRiskLabel,
        int outdoorActivityScore,
        String outdoorActivityLabel,
        String fishingConditionLabel
) {

    public static WeatherInsightsResponse from(WeatherInsightsData data) {
        return new WeatherInsightsResponse(
                data.city(),
                data.country(),
                data.moonPhase(),
                data.uvRiskLabel(),
                data.outdoorActivityScore(),
                data.outdoorActivityLabel(),
                data.fishingConditionLabel()
        );
    }
}
