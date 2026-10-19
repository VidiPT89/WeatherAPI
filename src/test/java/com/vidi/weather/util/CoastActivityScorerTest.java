package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidi.weather.model.ActivityScore;
import com.vidi.weather.model.CoastConditions;
import org.junit.jupiter.api.Test;

class CoastActivityScorerTest {

    private static CoastConditions sea(
            double swell, double period, double swellFrom, double wind, double windFrom, Integer facing) {
        return new CoastConditions(swell, period, swell, period, swellFrom, wind, windFrom,
                22.0, 0, 18.0, facing, false, false);
    }

    @Test
    void cleanMediumSwellWithOffshoreWind_isTheBestSurf() {
        ActivityScore score = CoastActivityScorer.surf(sea(1.5, 13, 290, 12, 90, 270));

        assertThat(score.score()).isEqualTo(10);
        assertThat(score.reasons()).containsExactly("SWELL_SOLID", "PERIOD_LONG", "WIND_OFFSHORE");
    }

    @Test
    void sameSwellWithStrongOnshoreWind_isMediocre() {
        ActivityScore score = CoastActivityScorer.surf(sea(1.5, 13, 290, 25, 270, 270));

        assertThat(score.score()).isEqualTo(7);
        assertThat(score.reasons()).contains("WIND_ONSHORE");
    }

    @Test
    void swellFromBehindTheBeach_isFlatThere() {
        // 1.5 m northerly swell, south-facing beach (Mareta).
        ActivityScore score = CoastActivityScorer.surf(sea(1.5, 13, 355, 5, 0, 175));

        assertThat(score.score()).isZero();
        assertThat(score.reasons()).containsExactly("SWELL_SHELTERED", "FLAT");
    }

    @Test
    void shortPeriodWindSwell_losesPoints() {
        ActivityScore score = CoastActivityScorer.surf(sea(0.8, 5, 270, 20, 0, 270));

        assertThat(score.score()).isEqualTo(4);
        assertThat(score.reasons()).containsExactly("SWELL_FUN", "PERIOD_SHORT");
    }

    @Test
    void hugeSwell_isNotRecommended() {
        ActivityScore score = CoastActivityScorer.surf(sea(5.0, 16, 290, 5, 90, 270));

        assertThat(score.score()).isLessThanOrEqualTo(6);
        assertThat(score.reasons()).contains("SWELL_HUGE");
    }

    @Test
    void surfIsUnknown_withoutAnyWaveHeight() {
        CoastConditions empty = new CoastConditions(null, null, null, null, null, 5.0, 0.0,
                20.0, 0, 18.0, 270, false, false);
        assertThat(CoastActivityScorer.surf(empty)).isNull();
        assertThat(CoastActivityScorer.fishing(empty)).isNull();
    }

    @Test
    void calmSeaLightWindAtTideTurnAndDawn_isGreatFishing() {
        CoastConditions calm = new CoastConditions(0.4, 6.0, 0.3, 8.0, 270.0, 8.0, 0.0,
                18.0, 0, 17.0, 270, true, true);

        ActivityScore score = CoastActivityScorer.fishing(calm);

        assertThat(score.score()).isEqualTo(10);
        assertThat(score.reasons()).containsExactly("SEA_CALM", "WIND_LIGHT", "TIDE_TURNING", "GOLDEN_HOUR");
    }

    @Test
    void roughSeaAndStrongWind_isPoorFishing() {
        ActivityScore score = CoastActivityScorer.fishing(sea(2.5, 10, 270, 35, 270, 270));

        assertThat(score.score()).isZero();
        assertThat(score.reasons()).containsExactly("SEA_ROUGH", "WIND_STRONG");
    }

    @Test
    void warmCalmDayWithWarmWater_isAGreatBeachDay() {
        CoastConditions summer = new CoastConditions(0.5, 8.0, 0.4, 9.0, 200.0, 8.0, 0.0,
                27.0, 0, 21.0, 190, false, false);

        ActivityScore score = CoastActivityScorer.beach(summer);

        assertThat(score.score()).isEqualTo(10);
        assertThat(score.reasons()).containsExactly("WARM", "WIND_LIGHT", "SEA_CALM", "WATER_WARM");
    }

    @Test
    void coolRainyWindyDay_isAPoorBeachDay() {
        CoastConditions winter = new CoastConditions(2.0, 10.0, 1.8, 12.0, 270.0, 30.0, 270.0,
                14.0, 80, 15.0, 270, false, false);

        ActivityScore score = CoastActivityScorer.beach(winter);

        assertThat(score.score()).isZero();
        assertThat(score.reasons()).containsExactly("COOL", "RAIN_LIKELY", "WIND_STRONG", "SEA_ROUGH", "WATER_COLD");
    }
}
