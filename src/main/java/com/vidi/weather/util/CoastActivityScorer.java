package com.vidi.weather.util;

import com.vidi.weather.model.ActivityScore;
import com.vidi.weather.model.CoastConditions;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores one hour at a spot, 0..10, for surfing, shore/small-boat fishing and a beach day. The
 * thresholds are rules of thumb for recreational use on the Portuguese coast, not a substitute
 * for local knowledge; every score carries the reason codes that moved it, so the app can say
 * why a window is good rather than show a bare number.
 */
public final class CoastActivityScorer {

    private static final double FLAT_METERS = 0.3;
    private static final double SMALL_METERS = 0.6;
    private static final double FUN_METERS = 1.0;
    private static final double SOLID_METERS = 2.5;
    private static final double BIG_METERS = 3.5;
    private static final double SHELTERED_REACH = 0.5;

    private static final double CALM_SEA_METERS = 0.5;
    private static final double SWIMMABLE_SEA_METERS = 0.8;
    private static final double MODERATE_SEA_METERS = 1.0;
    private static final double ROUGH_SEA_METERS = 1.8;

    private static final double LIGHT_WIND_KMH = 12.0;
    private static final double MODERATE_WIND_KMH = 15.0;
    private static final double FRESH_WIND_KMH = 25.0;
    private static final double OFFSHORE_MAX_KMH = 30.0;
    private static final double GALE_KMH = 40.0;

    private CoastActivityScorer() {
    }

    public static ActivityScore surf(CoastConditions c) {
        Double height = c.swellHeightMeters() != null ? c.swellHeightMeters() : c.waveHeightMeters();
        Double period = c.swellPeriodSeconds() != null ? c.swellPeriodSeconds() : c.wavePeriodSeconds();
        if (height == null) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        double reach = CoastExposure.swellReachFactor(c.swellDirectionDegrees(), c.facingDegrees());
        if (reach < SHELTERED_REACH) {
            reasons.add("SWELL_SHELTERED");
        }
        double effective = height * reach;

        int score;
        if (effective < FLAT_METERS) {
            reasons.add("FLAT");
            return new ActivityScore(effective < FLAT_METERS / 2 ? 0 : 1, reasons);
        } else if (effective < SMALL_METERS) {
            score = 3;
            reasons.add("SWELL_SMALL");
        } else if (effective < FUN_METERS) {
            score = 6;
            reasons.add("SWELL_FUN");
        } else if (effective <= SOLID_METERS) {
            score = 8;
            reasons.add("SWELL_SOLID");
        } else if (effective <= BIG_METERS) {
            score = 5;
            reasons.add("SWELL_BIG");
        } else {
            score = 2;
            reasons.add("SWELL_HUGE");
        }

        if (period != null) {
            if (period >= 12) {
                score += 2;
                reasons.add("PERIOD_LONG");
            } else if (period >= 9) {
                score += 1;
            } else if (period < 7) {
                score -= 2;
                reasons.add("PERIOD_SHORT");
            }
        }

        score += windAdjustmentForSurf(c, reasons);
        return new ActivityScore(clamp(score), reasons);
    }

    private static int windAdjustmentForSurf(CoastConditions c, List<String> reasons) {
        Double wind = c.windSpeedKmh();
        if (wind == null) {
            return 0;
        }
        String exposure = CoastExposure.windExposure(c.windDirectionDegrees(), c.facingDegrees());
        if (wind >= GALE_KMH) {
            reasons.add("WIND_STRONG");
            return -3;
        }
        if (CoastExposure.OFFSHORE.equals(exposure) && wind < OFFSHORE_MAX_KMH) {
            reasons.add("WIND_OFFSHORE");
            return 2;
        }
        if (wind < LIGHT_WIND_KMH) {
            reasons.add("WIND_LIGHT");
            return 1;
        }
        if (CoastExposure.ONSHORE.equals(exposure) && wind >= MODERATE_WIND_KMH) {
            reasons.add("WIND_ONSHORE");
            return -3;
        }
        if (wind >= FRESH_WIND_KMH) {
            reasons.add("WIND_STRONG");
            return -2;
        }
        return 0;
    }

    public static ActivityScore fishing(CoastConditions c) {
        Double height = c.waveHeightMeters();
        if (height == null) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        int score = 6;
        if (height <= CALM_SEA_METERS) {
            score += 2;
            reasons.add("SEA_CALM");
        } else if (height > ROUGH_SEA_METERS) {
            score -= 5;
            reasons.add("SEA_ROUGH");
        } else if (height > MODERATE_SEA_METERS) {
            score -= 2;
            reasons.add("SEA_MODERATE");
        }

        Double wind = c.windSpeedKmh();
        if (wind != null) {
            if (wind < MODERATE_WIND_KMH) {
                score += 1;
                reasons.add("WIND_LIGHT");
            } else if (wind > FRESH_WIND_KMH) {
                score -= 3;
                reasons.add("WIND_STRONG");
            } else {
                score -= 1;
            }
        }
        if (c.nearTideTurn()) {
            score += 1;
            reasons.add("TIDE_TURNING");
        }
        if (c.nearSunriseOrSunset()) {
            score += 1;
            reasons.add("GOLDEN_HOUR");
        }
        return new ActivityScore(clamp(score), reasons);
    }

    public static ActivityScore beach(CoastConditions c) {
        List<String> reasons = new ArrayList<>();
        int score = 5;

        Double air = c.airTemperatureCelsius();
        if (air != null) {
            if (air >= 24) {
                score += 2;
                reasons.add("WARM");
            } else if (air >= 20) {
                score += 1;
            } else if (air < 17) {
                score -= 2;
                reasons.add("COOL");
            }
        }

        Integer rain = c.precipitationProbability();
        if (rain != null) {
            if (rain >= 50) {
                score -= 4;
                reasons.add("RAIN_LIKELY");
            } else if (rain >= 20) {
                score -= 1;
            }
        }

        Double wind = c.windSpeedKmh();
        if (wind != null) {
            String exposure = CoastExposure.windExposure(c.windDirectionDegrees(), c.facingDegrees());
            if (wind >= FRESH_WIND_KMH) {
                score -= 3;
                reasons.add("WIND_STRONG");
            } else if (CoastExposure.ONSHORE.equals(exposure) && wind >= MODERATE_WIND_KMH) {
                score -= 1;
                reasons.add("WIND_ONSHORE");
            } else if (wind < LIGHT_WIND_KMH) {
                score += 1;
                reasons.add("WIND_LIGHT");
            }
        }

        Double waves = c.waveHeightMeters();
        if (waves != null) {
            double reach = CoastExposure.swellReachFactor(c.swellDirectionDegrees(), c.facingDegrees());
            double atBeach = waves * Math.max(reach, 0.5);
            if (atBeach <= SWIMMABLE_SEA_METERS) {
                score += 1;
                reasons.add("SEA_CALM");
            } else if (atBeach > 1.5) {
                score -= 2;
                reasons.add("SEA_ROUGH");
            }
        }

        Double water = c.waterTemperatureCelsius();
        if (water != null) {
            if (water >= 19) {
                score += 1;
                reasons.add("WATER_WARM");
            } else if (water < 16) {
                score -= 1;
                reasons.add("WATER_COLD");
            }
        }
        return new ActivityScore(clamp(score), reasons);
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(10, score));
    }
}
