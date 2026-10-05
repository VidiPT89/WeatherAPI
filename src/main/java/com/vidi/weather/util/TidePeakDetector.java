package com.vidi.weather.util;

import com.vidi.weather.model.TideEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Derives high/low tide events from an hourly sea-level-height time series by finding
 * local maxima/minima. Open-Meteo's marine model does not publish tide extrema directly,
 * only the underlying hourly {@code sea_level_height_msl} curve.
 */
public final class TidePeakDetector {

    private TidePeakDetector() {
    }

    /**
     * Same as {@link #detect}, but keeps only the events on the first day of the series. Open-Meteo
     * returns local times ({@code timezone=auto}) for the whole forecast range, while the clients
     * present this list as today's tides. Detection still runs over the full series, so a turning
     * point at 23:00 can use the next day's first reading as its neighbour.
     */
    public static List<TideEvent> detectToday(List<String> times, List<Double> heights) {
        if (times == null || times.isEmpty()) {
            return new ArrayList<>();
        }
        String first = times.get(0);
        String today = first.length() >= 10 ? first.substring(0, 10) : first;
        return detect(times, heights).stream()
                .filter(event -> event.time().startsWith(today))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<TideEvent> detect(List<String> times, List<Double> heights) {
        List<TideEvent> events = new ArrayList<>();
        if (times == null || heights == null) {
            return events;
        }

        int count = Math.min(times.size(), heights.size());
        for (int i = 1; i < count - 1; i++) {
            Double previous = heights.get(i - 1);
            Double current = heights.get(i);
            Double next = heights.get(i + 1);
            if (previous == null || current == null || next == null) {
                continue;
            }

            if (current > previous && current > next) {
                events.add(new TideEvent(TideEvent.HIGH, times.get(i)));
            } else if (current < previous && current < next) {
                events.add(new TideEvent(TideEvent.LOW, times.get(i)));
            }
        }
        return events;
    }
}
