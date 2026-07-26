package com.vidi.weather.util;

/**
 * A rough recreational-surf conditions label from forecasted wave height and period. A longer
 * period means a more organized, less choppy swell; too little swell is flat, too much (or too
 * short a period) is only rideable by advanced surfers.
 */
public final class SurfConditionScorer {

    private static final double FLAT_WAVE_HEIGHT_METERS = 0.4;
    private static final double LARGE_WAVE_HEIGHT_METERS = 3.0;
    private static final double ORGANIZED_SWELL_PERIOD_SECONDS = 8.0;
    private static final double CHOPPY_SWELL_PERIOD_SECONDS = 6.0;

    private SurfConditionScorer() {
    }

    public static String label(Double waveHeightMeters, Double wavePeriodSeconds) {
        if (waveHeightMeters == null || wavePeriodSeconds == null) {
            return null;
        }
        if (waveHeightMeters < FLAT_WAVE_HEIGHT_METERS) {
            return "Poor";
        }
        if (waveHeightMeters <= LARGE_WAVE_HEIGHT_METERS && wavePeriodSeconds >= ORGANIZED_SWELL_PERIOD_SECONDS) {
            return "Good";
        }
        if (waveHeightMeters <= LARGE_WAVE_HEIGHT_METERS && wavePeriodSeconds >= CHOPPY_SWELL_PERIOD_SECONDS) {
            return "Fair";
        }
        return "Poor";
    }
}
