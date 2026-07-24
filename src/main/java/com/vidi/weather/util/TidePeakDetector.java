package com.vidi.weather.util;

import com.vidi.weather.model.TideEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives high/low tide events from an hourly sea-level-height time series by finding
 * local maxima/minima. Open-Meteo's marine model does not publish tide extrema directly,
 * only the underlying hourly {@code sea_level_height_msl} curve.
 */
public final class TidePeakDetector {

    private TidePeakDetector() {
    }

    public static List<TideEvent> detect(List<String> times, List<Double> heights) {
        List<TideEvent> events = new ArrayList<>();
        if (times == null || heights == null) {
            return events;
        }

        int count = Math.min(times.size(), heights.size());
        for (int i = 1; i < count - 1; i++) {
            double previous = heights.get(i - 1);
            double current = heights.get(i);
            double next = heights.get(i + 1);

            if (current > previous && current > next) {
                events.add(new TideEvent(TideEvent.HIGH, times.get(i)));
            } else if (current < previous && current < next) {
                events.add(new TideEvent(TideEvent.LOW, times.get(i)));
            }
        }
        return events;
    }
}
