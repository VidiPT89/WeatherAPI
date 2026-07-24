package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidi.weather.model.Units;
import org.junit.jupiter.api.Test;

class FishingConditionScorerTest {

    @Test
    void returnsNull_whenNoMarineDataIsAvailable() {
        assertThat(FishingConditionScorer.label(null, 10.0, Units.METRIC)).isNull();
    }

    @Test
    void returnsGood_forCalmSeaAndLightWind() {
        assertThat(FishingConditionScorer.label(0.3, 10.0, Units.METRIC)).isEqualTo("Good");
    }

    @Test
    void returnsFair_forModerateSeaAndWind() {
        assertThat(FishingConditionScorer.label(0.9, 22.0, Units.METRIC)).isEqualTo("Fair");
    }

    @Test
    void returnsPoor_forRoughSeaOrStrongWind() {
        assertThat(FishingConditionScorer.label(2.0, 40.0, Units.METRIC)).isEqualTo("Poor");
    }

    @Test
    void convertsImperialWindSpeed_beforeComparingAgainstMetricThresholds() {
        // ~9.3 mph -> ~15 km/h, right at the "Good" boundary
        assertThat(FishingConditionScorer.label(0.3, 9.3, Units.IMPERIAL)).isEqualTo("Good");
    }
}
