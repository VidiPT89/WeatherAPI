package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidi.weather.model.MoonPhaseInfo;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MoonPhaseCalculatorTest {

    // Reference dates from public almanac data. The synodic-month approximation used here can
    // drift by about a day, so phase-boundary dates are deliberately avoided in these cases.
    @Test
    void returnsNewMoon_onTheReferenceNewMoonDate() {
        MoonPhaseInfo info = MoonPhaseCalculator.calculate(LocalDate.of(2000, 1, 6));

        assertThat(info.phase()).isEqualTo("New Moon");
        assertThat(info.illuminationPercent()).isBetween(0, 5);
    }

    @Test
    void returnsFullMoon_halfwayThroughTheSynodicMonth() {
        MoonPhaseInfo info = MoonPhaseCalculator.calculate(LocalDate.of(2000, 1, 21));

        assertThat(info.phase()).isEqualTo("Full Moon");
        assertThat(info.illuminationPercent()).isBetween(95, 100);
    }

    @Test
    void returnsFirstQuarter_aQuarterIntoTheSynodicMonth() {
        MoonPhaseInfo info = MoonPhaseCalculator.calculate(LocalDate.of(2000, 1, 13));

        assertThat(info.phase()).isEqualTo("First Quarter");
    }

    @Test
    void handlesDatesBeforeTheReferenceNewMoon() {
        MoonPhaseInfo info = MoonPhaseCalculator.calculate(LocalDate.of(1999, 12, 1));

        assertThat(info.phase()).isNotBlank();
        assertThat(info.illuminationPercent()).isBetween(0, 100);
    }
}
