package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UvRiskLabelerTest {

    @ParameterizedTest
    @CsvSource({
            "0, Low",
            "2.9, Low",
            "3, Moderate",
            "5.9, Moderate",
            "6, High",
            "7.9, High",
            "8, Very High",
            "10.9, Very High",
            "11, Extreme",
            "15, Extreme"
    })
    void labelsUvIndex_accordingToWhoScale(double uvIndexMax, String expectedLabel) {
        assertThat(UvRiskLabeler.label(uvIndexMax)).isEqualTo(expectedLabel);
    }
}
