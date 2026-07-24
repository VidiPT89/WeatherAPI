package com.vidi.weather.util;

import com.vidi.weather.model.MoonPhaseInfo;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Computes the moon phase for a given date from the synodic month cycle — no external data
 * needed. Accurate to within about a day, which is fine for a casual "what's the moon doing
 * tonight" feature rather than an astronomical almanac.
 */
public final class MoonPhaseCalculator {

    private static final LocalDate KNOWN_NEW_MOON = LocalDate.of(2000, 1, 6);
    private static final double SYNODIC_MONTH_DAYS = 29.53058867;

    private MoonPhaseCalculator() {
    }

    public static MoonPhaseInfo calculate(LocalDate date) {
        long daysSinceKnownNewMoon = ChronoUnit.DAYS.between(KNOWN_NEW_MOON, date);
        double age = daysSinceKnownNewMoon % SYNODIC_MONTH_DAYS;
        if (age < 0) {
            age += SYNODIC_MONTH_DAYS;
        }
        double fraction = age / SYNODIC_MONTH_DAYS;

        int illumination = (int) Math.round((1 - Math.cos(2 * Math.PI * fraction)) / 2 * 100);
        return new MoonPhaseInfo(phaseNameFor(fraction), illumination);
    }

    private static String phaseNameFor(double fraction) {
        if (fraction < 0.0625 || fraction >= 0.9375) {
            return "New Moon";
        } else if (fraction < 0.1875) {
            return "Waxing Crescent";
        } else if (fraction < 0.3125) {
            return "First Quarter";
        } else if (fraction < 0.4375) {
            return "Waxing Gibbous";
        } else if (fraction < 0.5625) {
            return "Full Moon";
        } else if (fraction < 0.6875) {
            return "Waning Gibbous";
        } else if (fraction < 0.8125) {
            return "Last Quarter";
        } else {
            return "Waning Crescent";
        }
    }
}
