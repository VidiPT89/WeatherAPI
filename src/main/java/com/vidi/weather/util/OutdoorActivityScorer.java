package com.vidi.weather.util;

import com.vidi.weather.model.Units;

/**
 * A simple 0-100 "good day to be outside" score. Not a scientific model — just additive
 * penalties against an ideal baseline (comfortable temperature, light wind, low rain chance,
 * moderate UV), each capped so no single factor can dominate the score.
 */
public final class OutdoorActivityScorer {

    private static final double IDEAL_TEMPERATURE_CELSIUS = 21.0;
    private static final double CALM_WIND_KMH = 20.0;
    private static final double MODERATE_UV_INDEX = 6.0;

    private OutdoorActivityScorer() {
    }

    public static int score(
            double temperature, double windSpeed, int precipitationProbabilityPercent, double uvIndexMax, Units units) {
        double temperatureCelsius = units == Units.IMPERIAL ? toCelsius(temperature) : temperature;
        double windSpeedKmh = units == Units.IMPERIAL ? toKmh(windSpeed) : windSpeed;

        double temperaturePenalty = Math.min(40, Math.abs(temperatureCelsius - IDEAL_TEMPERATURE_CELSIUS) * 2);
        double windPenalty = Math.min(30, Math.max(0, windSpeedKmh - CALM_WIND_KMH));
        double precipitationPenalty = Math.min(30, precipitationProbabilityPercent * 0.3);
        double uvPenalty = Math.min(20, Math.max(0, (uvIndexMax - MODERATE_UV_INDEX) * 5));

        double score = 100 - temperaturePenalty - windPenalty - precipitationPenalty - uvPenalty;
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    public static String label(int score) {
        if (score >= 80) {
            return "Great";
        } else if (score >= 60) {
            return "Good";
        } else if (score >= 40) {
            return "Fair";
        }
        return "Poor";
    }

    private static double toCelsius(double fahrenheit) {
        return (fahrenheit - 32) * 5 / 9;
    }

    private static double toKmh(double mph) {
        return mph * 1.60934;
    }
}
