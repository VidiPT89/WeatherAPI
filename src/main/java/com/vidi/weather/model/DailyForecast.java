package com.vidi.weather.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code waveHeightMax}/{@code wavePeriodMax} are {@code null} for inland cities with no marine
 * forecast coverage, in which case {@code fishingConditionLabel}/{@code surfConditionLabel} are
 * also {@code null} rather than a misleading guess.
 */
public record DailyForecast(
        LocalDate date,
        double temperatureMax,
        double temperatureMin,
        String description,
        LocalDateTime sunrise,
        LocalDateTime sunset,
        double uvIndexMax,
        int precipitationProbabilityMax,
        double windSpeedMax,
        Double waveHeightMax,
        Double wavePeriodMax,
        boolean rainLikely,
        String uvRiskLabel,
        String outdoorActivityLabel,
        String fishingConditionLabel,
        String surfConditionLabel
) {
}
