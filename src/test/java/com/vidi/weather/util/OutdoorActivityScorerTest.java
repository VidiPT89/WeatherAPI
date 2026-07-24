package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidi.weather.model.Units;
import org.junit.jupiter.api.Test;

class OutdoorActivityScorerTest {

    @Test
    void scoresNearlyPerfect_onAnIdealDay() {
        int score = OutdoorActivityScorer.score(21.0, 5.0, 0, 3.0, Units.METRIC);

        assertThat(score).isGreaterThanOrEqualTo(90);
        assertThat(OutdoorActivityScorer.label(score)).isEqualTo("Great");
    }

    @Test
    void scoresPoorly_onAHotWindyRainyDay() {
        int score = OutdoorActivityScorer.score(38.0, 45.0, 90, 10.0, Units.METRIC);

        assertThat(score).isLessThan(40);
        assertThat(OutdoorActivityScorer.label(score)).isEqualTo("Poor");
    }

    @Test
    void isClampedToZero_neverNegative() {
        int score = OutdoorActivityScorer.score(45.0, 100.0, 100, 15.0, Units.METRIC);

        assertThat(score).isBetween(0, 100);
    }

    @Test
    void isUnitAware_equivalentImperialInputsScoreTheSameAsMetric() {
        int metricScore = OutdoorActivityScorer.score(21.0, 10.0, 20, 5.0, Units.METRIC);
        int imperialScore = OutdoorActivityScorer.score(69.8, 6.21, 20, 5.0, Units.IMPERIAL);

        assertThat(imperialScore).isCloseTo(metricScore, org.assertj.core.data.Offset.offset(2));
    }
}
