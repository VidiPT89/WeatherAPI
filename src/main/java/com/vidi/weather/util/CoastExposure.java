package com.vidi.weather.util;

/**
 * Relates a wind or swell direction to the way a beach faces. Both Open-Meteo directions are the
 * direction the wind/swell comes FROM, and a beach's facing is the direction it looks out to sea,
 * so a direction equal to the facing means it comes straight in from the sea.
 */
public final class CoastExposure {

    public static final String OFFSHORE = "offshore";
    public static final String CROSS_SHORE = "cross-shore";
    public static final String ONSHORE = "onshore";

    private static final double ONSHORE_MAX_ANGLE = 60.0;
    private static final double OFFSHORE_MIN_ANGLE = 120.0;
    private static final double FULL_SWELL_MAX_ANGLE = 45.0;
    private static final double BLOCKED_SWELL_MIN_ANGLE = 160.0;

    private CoastExposure() {
    }

    /** Smallest angle between two compass directions, 0..180. */
    public static double angleBetween(double a, double b) {
        double diff = Math.abs(((a - b) % 360 + 360) % 360);
        return diff > 180 ? 360 - diff : diff;
    }

    /** {@code null} when the beach orientation or the wind direction is unknown. */
    public static String windExposure(Double windFromDegrees, Integer facingDegrees) {
        if (windFromDegrees == null || facingDegrees == null) {
            return null;
        }
        double angle = angleBetween(windFromDegrees, facingDegrees);
        if (angle <= ONSHORE_MAX_ANGLE) {
            return ONSHORE;
        }
        if (angle >= OFFSHORE_MIN_ANGLE) {
            return OFFSHORE;
        }
        return CROSS_SHORE;
    }

    /**
     * Share of the open-sea swell that reaches the beach, 0..1: all of it when it comes from
     * within 45 degrees of the facing, falling off linearly to none when it comes from (nearly)
     * behind the coastline. The fall-off is gradual because swell wraps around headlands: a big
     * north-west swell still reaches south-facing Carcavelos, only much smaller. Without an
     * orientation the open-sea value is used as is.
     */
    public static double swellReachFactor(Double swellFromDegrees, Integer facingDegrees) {
        if (swellFromDegrees == null || facingDegrees == null) {
            return 1.0;
        }
        double angle = angleBetween(swellFromDegrees, facingDegrees);
        if (angle <= FULL_SWELL_MAX_ANGLE) {
            return 1.0;
        }
        if (angle >= BLOCKED_SWELL_MIN_ANGLE) {
            return 0.0;
        }
        return (BLOCKED_SWELL_MIN_ANGLE - angle) / (BLOCKED_SWELL_MIN_ANGLE - FULL_SWELL_MAX_ANGLE);
    }
}
