package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidi.weather.model.TideEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class TidePeakDetectorTest {

    @Test
    void detectsOneHighAndOneLow_inASimpleCurve() {
        List<String> times = List.of("00:00", "01:00", "02:00", "03:00", "04:00");
        List<Double> heights = List.of(0.2, 0.9, 0.3, -0.6, 0.1);

        List<TideEvent> events = TidePeakDetector.detect(times, heights);

        assertThat(events).containsExactly(
                new TideEvent(TideEvent.HIGH, "01:00"),
                new TideEvent(TideEvent.LOW, "03:00")
        );
    }

    @Test
    void returnsEmptyList_whenHeightsIsNull() {
        List<String> times = List.of("00:00", "01:00");

        assertThat(TidePeakDetector.detect(times, null)).isEmpty();
    }

    @Test
    void returnsEmptyList_whenTimesIsNull() {
        List<Double> heights = List.of(0.2, 0.5);

        assertThat(TidePeakDetector.detect(null, heights)).isEmpty();
    }

    @Test
    void ignoresMonotonicSeries_withNoTurningPoints() {
        List<String> times = List.of("00:00", "01:00", "02:00", "03:00");
        List<Double> heights = List.of(0.1, 0.2, 0.3, 0.4);

        assertThat(TidePeakDetector.detect(times, heights)).isEmpty();
    }

    @Test
    void returnsEmptyList_withFewerThanThreeReadings() {
        List<String> times = List.of("00:00", "01:00");
        List<Double> heights = List.of(0.2, 0.9);

        assertThat(TidePeakDetector.detect(times, heights)).isEmpty();
    }
}
