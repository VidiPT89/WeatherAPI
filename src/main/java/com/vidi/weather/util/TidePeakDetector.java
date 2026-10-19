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

    /**
     * A turning point can sit on a plateau of equal hourly readings (e.g. 0.22, 0.22 when the real
     * peak falls on the half hour), so a run of equal values counts as one point, reported at its
     * first hour, and is compared with the readings on either side of the whole run.
     */
    public static List<TideEvent> detect(List<String> times, List<Double> heights) {
        List<TideEvent> events = new ArrayList<>();
        if (times == null || heights == null) {
            return events;
        }

        int count = Math.min(times.size(), heights.size());
        int i = 1;
        while (i < count - 1) {
            Double previous = heights.get(i - 1);
            Double current = heights.get(i);
            if (previous == null || current == null) {
                i++;
                continue;
            }
            int runEnd = i;
            while (runEnd + 1 < count && current.equals(heights.get(runEnd + 1))) {
                runEnd++;
            }
            if (runEnd + 1 >= count) {
                break;
            }
            Double next = heights.get(runEnd + 1);
            if (next != null) {
                if (current > previous && current > next) {
                    events.add(new TideEvent(TideEvent.HIGH, times.get(i)));
                } else if (current < previous && current < next) {
                    events.add(new TideEvent(TideEvent.LOW, times.get(i)));
                }
            }
            i = runEnd + 1;
        }
        return events;
    }
}
