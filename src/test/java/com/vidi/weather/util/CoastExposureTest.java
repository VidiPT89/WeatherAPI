package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoastExposureTest {

    @ParameterizedTest
    @CsvSource({
            "270, 270, 0",
            "350, 10, 20",   // wraps around north
            "10, 350, 20",
            "90, 270, 180",
            "0, 225, 135",
    })
    void measuresTheSmallestAngleBetweenTwoDirections(double a, double b, double expected) {
        assertThat(CoastExposure.angleBetween(a, b)).isCloseTo(expected, within(0.001));
    }

    @ParameterizedTest
    @CsvSource({
            "270, 270, onshore",      // west wind on a west-facing beach blows in from the sea
            "90, 270, offshore",      // east wind blows from the land out to sea
            "0, 270, cross-shore",    // north wind runs along a west-facing beach
            "20, 190, offshore",      // Carcavelos (faces south) in a northerly
            "330, 280, onshore",
    })
    void classifiesWindAgainstTheBeachFacing(double windFrom, int facing, String expected) {
        assertThat(CoastExposure.windExposure(windFrom, facing)).isEqualTo(expected);
    }

    @Test
    void windExposureIsUnknown_withoutAFacingOrADirection() {
        assertThat(CoastExposure.windExposure(270.0, null)).isNull();
        assertThat(CoastExposure.windExposure(null, 270)).isNull();
    }

    @Test
    void swellReachesFully_whenItComesFromInFrontOfTheBeach() {
        assertThat(CoastExposure.swellReachFactor(300.0, 270)).isEqualTo(1.0);
    }

    @Test
    void swellIsBlocked_whenItComesFromBehindTheCoastline() {
        // A northerly swell at Sagres' south-facing Mareta.
        assertThat(CoastExposure.swellReachFactor(355.0, 175)).isEqualTo(0.0);
    }

    @Test
    void swellReachFallsOffBetweenTheTwoLimits() {
        assertThat(CoastExposure.swellReachFactor(12.5, 270)).isCloseTo(0.5, within(0.001));
        // A north-west swell still wraps into south-facing Carcavelos, at about a third of its size.
        assertThat(CoastExposure.swellReachFactor(315.0, 190)).isCloseTo(0.304, within(0.001));
    }

    @Test
    void swellReachIsFull_withoutAnOrientation() {
        assertThat(CoastExposure.swellReachFactor(315.0, null)).isEqualTo(1.0);
        assertThat(CoastExposure.swellReachFactor(null, 270)).isEqualTo(1.0);
    }
}
