package com.vidi.weather.util;

import com.vidi.weather.model.ActivityScore;
import com.vidi.weather.model.ActivityWindow;
import com.vidi.weather.model.CoastConditions;
import com.vidi.weather.model.CoastDay;
import com.vidi.weather.model.CoastHour;
import com.vidi.weather.model.CoastOutlookData;
import com.vidi.weather.model.CoastSpot;
import com.vidi.weather.model.TideEvent;
import com.vidi.weather.provider.openmeteo.CoastMarineResponse;
import com.vidi.weather.provider.openmeteo.CoastWindResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Combines the sea and wind series into hourly scores and, per day, the best daylight window for
 * each activity. Pure (the current time is passed in), so it is tested without any HTTP.
 */
public final class CoastOutlookBuilder {

    static final int WINDOW_HOURS = 3;
    private static final Duration TIDE_TURN_MARGIN = Duration.ofHours(2);
    private static final Duration GOLDEN_HOUR_MARGIN = Duration.ofHours(1);

    private CoastOutlookBuilder() {
    }

    private record ScoredHour(LocalDateTime time, ActivityScore surf, ActivityScore fishing, ActivityScore beach) {
    }

    public static CoastOutlookData build(
            CoastSpot spot, String provider, CoastMarineResponse sea, CoastWindResponse wind, LocalDateTime now) {
        CoastMarineResponse.Hourly s = sea.hourly();
        CoastWindResponse.Hourly w = wind.hourly();

        Map<String, Integer> windIndex = new HashMap<>();
        for (int i = 0; i < w.time().size(); i++) {
            windIndex.put(w.time().get(i), i);
        }

        List<TideEvent> tides = TidePeakDetector.detect(s.time(), s.seaLevelHeightMsl());
        Map<LocalDate, LocalDateTime[]> daylight = daylightByDate(wind.daily());

        List<CoastHour> hours = new ArrayList<>();
        List<ScoredHour> scored = new ArrayList<>();
        for (int i = 0; i < s.time().size(); i++) {
            String timeText = s.time().get(i);
            LocalDateTime time = LocalDateTime.parse(timeText);
            Integer wi = windIndex.get(timeText);

            Double windSpeed = wi == null ? null : at(w.windSpeed(), wi);
            Double windDirection = wi == null ? null : at(w.windDirection(), wi);
            Double airTemperature = wi == null ? null : at(w.temperature(), wi);
            Integer rain = wi == null ? null : at(w.precipitationProbability(), wi);
            LocalDateTime[] sun = daylight.get(time.toLocalDate());

            CoastConditions conditions = new CoastConditions(
                    at(s.waveHeight(), i), at(s.wavePeriod(), i),
                    at(s.swellWaveHeight(), i), at(s.swellWavePeriod(), i), at(s.swellWaveDirection(), i),
                    windSpeed, windDirection, airTemperature, rain, at(s.seaSurfaceTemperature(), i),
                    spot.facingDegrees(),
                    isNearTideTurn(time, tides),
                    sun != null && (within(time, sun[0], GOLDEN_HOUR_MARGIN) || within(time, sun[1], GOLDEN_HOUR_MARGIN)));

            ActivityScore surf = CoastActivityScorer.surf(conditions);
            ActivityScore fishing = CoastActivityScorer.fishing(conditions);
            ActivityScore beach = isDaylight(time, sun) ? CoastActivityScorer.beach(conditions) : null;
            scored.add(new ScoredHour(time, surf, fishing, beach));

            hours.add(new CoastHour(
                    time,
                    conditions.waveHeightMeters(), conditions.wavePeriodSeconds(), at(s.waveDirection(), i),
                    conditions.swellHeightMeters(), conditions.swellPeriodSeconds(), conditions.swellDirectionDegrees(),
                    windSpeed, wi == null ? null : at(w.windGusts(), wi), windDirection,
                    CoastExposure.windExposure(windDirection, spot.facingDegrees()),
                    at(s.seaLevelHeightMsl(), i), conditions.waterTemperatureCelsius(), airTemperature, rain,
                    scoreOf(surf), scoreOf(fishing), scoreOf(beach)));
        }

        List<CoastDay> days = new ArrayList<>();
        LocalDateTime currentHour = now.truncatedTo(ChronoUnit.HOURS);
        for (Map.Entry<LocalDate, LocalDateTime[]> entry : daylight.entrySet()) {
            LocalDateTime[] sun = entry.getValue();
            List<ScoredHour> candidates = scored.stream()
                    .filter(h -> h.time().toLocalDate().equals(entry.getKey()))
                    .filter(h -> isDaylight(h.time(), sun))
                    .filter(h -> !h.time().isBefore(currentHour))
                    .toList();
            days.add(new CoastDay(
                    entry.getKey(), sun[0], sun[1],
                    bestWindow(candidates, ScoredHour::surf),
                    bestWindow(candidates, ScoredHour::fishing),
                    bestWindow(candidates, ScoredHour::beach)));
        }
        days.sort((a, b) -> a.date().compareTo(b.date()));

        return new CoastOutlookData(spot, provider, wind.utcOffsetSeconds(), hours, tides, days);
    }

    /**
     * The run of {@link #WINDOW_HOURS} consecutive hours with the highest average score (the
     * earliest one on a tie), or a shorter run when fewer hours of daylight are left. Its reasons
     * are those of its best hour.
     */
    private static ActivityWindow bestWindow(List<ScoredHour> scoredHours, Function<ScoredHour, ActivityScore> activity) {
        ActivityWindow best = null;
        double bestAverage = -1;
        for (int start = 0; start < scoredHours.size(); start++) {
            int end = Math.min(start + WINDOW_HOURS, scoredHours.size());
            if (end - start < WINDOW_HOURS && best != null) {
                break;
            }
            int total = 0;
            ActivityScore peak = null;
            boolean complete = true;
            for (int i = start; i < end; i++) {
                ActivityScore score = activity.apply(scoredHours.get(i));
                boolean contiguous = i == start
                        || scoredHours.get(i).time().equals(scoredHours.get(i - 1).time().plusHours(1));
                if (score == null || !contiguous) {
                    complete = false;
                    break;
                }
                total += score.score();
                if (peak == null || score.score() > peak.score()) {
                    peak = score;
                }
            }
            if (!complete) {
                continue;
            }
            double average = (double) total / (end - start);
            if (average > bestAverage) {
                bestAverage = average;
                best = new ActivityWindow(
                        scoredHours.get(start).time(),
                        scoredHours.get(end - 1).time().plusHours(1),
                        (int) Math.round(average),
                        peak.reasons());
            }
        }
        return best;
    }

    private static Map<LocalDate, LocalDateTime[]> daylightByDate(CoastWindResponse.Daily daily) {
        Map<LocalDate, LocalDateTime[]> result = new HashMap<>();
        if (daily == null || daily.time() == null) {
            return result;
        }
        for (int i = 0; i < daily.time().size(); i++) {
            String sunrise = at(daily.sunrise(), i);
            String sunset = at(daily.sunset(), i);
            if (sunrise != null && sunset != null) {
                result.put(LocalDate.parse(daily.time().get(i)),
                        new LocalDateTime[] {LocalDateTime.parse(sunrise), LocalDateTime.parse(sunset)});
            }
        }
        return result;
    }

    /** An hour counts as daylight when any part of it is between sunrise and sunset. */
    private static boolean isDaylight(LocalDateTime hour, LocalDateTime[] sun) {
        return sun != null && hour.plusHours(1).isAfter(sun[0]) && hour.isBefore(sun[1]);
    }

    private static boolean isNearTideTurn(LocalDateTime time, List<TideEvent> tides) {
        return tides.stream().anyMatch(t -> within(time, LocalDateTime.parse(t.time()), TIDE_TURN_MARGIN));
    }

    private static boolean within(LocalDateTime a, LocalDateTime b, Duration margin) {
        return Duration.between(a, b).abs().compareTo(margin) <= 0;
    }

    private static Integer scoreOf(ActivityScore score) {
        return score == null ? null : score.score();
    }

    private static <T> T at(List<T> values, int index) {
        return (values == null || index >= values.size()) ? null : values.get(index);
    }
}
