package com.vidi.weather.util;

import com.vidi.weather.model.Units;

/**
 * A rough recreational-fishing conditions label from sea state (wave height) and wind speed.
 * Calm, small waves and light wind make boat handling and bite detection easier; the reverse
 * makes for a rough, less pleasant day out.
 */
public final class FishingConditionScorer {

    private static final double CALM_WAVE_HEIGHT_METERS = 0.5;
    private static final double MODERATE_WAVE_HEIGHT_METERS = 1.2;
    private static final double CALM_WIND_KMH = 15.0;
    private static final double MODERATE_WIND_KMH = 25.0;

    private FishingConditionScorer() {
    }

    public static String label(Double waveHeightMeters, double windSpeed, Units units) {
        if (waveHeightMeters == null) {
            return null;
        }

        double windSpeedKmh = units == Units.IMPERIAL ? windSpeed * 1.60934 : windSpeed;

        if (waveHeightMeters <= CALM_WAVE_HEIGHT_METERS && windSpeedKmh <= CALM_WIND_KMH) {
            return "Good";
        } else if (waveHeightMeters <= MODERATE_WAVE_HEIGHT_METERS && windSpeedKmh <= MODERATE_WIND_KMH) {
            return "Fair";
        }
        return "Poor";
    }
}
