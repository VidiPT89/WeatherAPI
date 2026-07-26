package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SurfConditionScorerTest {

    @ParameterizedTest
    @CsvSource({
            "0.2, 10, Poor",   // flat -- no swell to ride regardless of period
            "1.5, 9, Good",    // solid size, organized long-period swell
            "1.5, 7, Fair",    // solid size, shorter/choppier period
            "1.5, 5, Poor",    // solid size, but too short a period -- wind chop
            "4.0, 9, Poor",    // too big for an average recreational surfer
    })
    void labelsSurfConditions_fromWaveHeightAndPeriod(double waveHeightMeters, double wavePeriodSeconds, String expectedLabel) {
        assertThat(SurfConditionScorer.label(waveHeightMeters, wavePeriodSeconds)).isEqualTo(expectedLabel);
    }

    @Test
    void returnsNull_whenWaveHeightIsMissing() {
        assertThat(SurfConditionScorer.label(null, 9.0)).isNull();
    }

    @Test
    void returnsNull_whenWavePeriodIsMissing() {
        assertThat(SurfConditionScorer.label(1.2, null)).isNull();
    }
}
