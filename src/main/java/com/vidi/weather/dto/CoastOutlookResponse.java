package com.vidi.weather.dto;

import com.vidi.weather.model.ActivityWindow;
import com.vidi.weather.model.CoastHour;
import com.vidi.weather.model.CoastOutlookResult;
import com.vidi.weather.model.TideEvent;
import com.vidi.weather.model.Units;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The coast outlook as sent to clients. Wave heights and sea level are always meters and periods
 * seconds; wind and temperatures follow {@code units} (km/h and Celsius, or mph and Fahrenheit).
 * Times are the spot's local time without an offset, like the forecast endpoint's.
 */
public record CoastOutlookResponse(
        CoastSpotResponse spot,
        String units,
        String provider,
        boolean fromCache,
        int utcOffsetSeconds,
        List<Hour> hours,
        List<TideEvent> tideEvents,
        List<Day> days
) {

    public record Hour(
            LocalDateTime time,
            Double waveHeightMeters,
            Double wavePeriodSeconds,
            Double waveDirectionDegrees,
            Double swellHeightMeters,
            Double swellPeriodSeconds,
            Double swellDirectionDegrees,
            Double windSpeed,
            Double windGusts,
            Double windDirectionDegrees,
            String windExposure,
            Double seaLevelMeters,
            Double waterTemperature,
            Double airTemperature,
            Integer precipitationProbability,
            Integer surfScore,
            Integer fishingScore,
            Integer beachScore
    ) {
    }

    public record Day(
            LocalDate date,
            LocalDateTime sunrise,
            LocalDateTime sunset,
            ActivityWindow surf,
            ActivityWindow fishing,
            ActivityWindow beach
    ) {
    }

    private static final double KMH_TO_MPH = 0.621371;

    public static CoastOutlookResponse from(CoastOutlookResult result, Units units) {
        var data = result.data();
        List<Hour> hours = data.hours().stream().map(h -> toHour(h, units)).toList();
        List<Day> days = data.days().stream()
                .map(d -> new Day(d.date(), d.sunrise(), d.sunset(), d.surf(), d.fishing(), d.beach()))
                .toList();
        return new CoastOutlookResponse(
                CoastSpotResponse.from(data.spot(), null),
                units.name().toLowerCase(),
                data.provider(),
                result.fromCache(),
                data.utcOffsetSeconds(),
                hours,
                data.tideEvents(),
                days);
    }

    private static Hour toHour(CoastHour h, Units units) {
        return new Hour(
                h.time(),
                h.waveHeightMeters(), h.wavePeriodSeconds(), h.waveDirectionDegrees(),
                h.swellHeightMeters(), h.swellPeriodSeconds(), h.swellDirectionDegrees(),
                speed(h.windSpeedKmh(), units), speed(h.windGustsKmh(), units), h.windDirectionDegrees(),
                h.windExposure(),
                h.seaLevelMeters(),
                temperature(h.waterTemperatureCelsius(), units), temperature(h.airTemperatureCelsius(), units),
                h.precipitationProbability(),
                h.surfScore(), h.fishingScore(), h.beachScore());
    }

    private static Double speed(Double kmh, Units units) {
        return kmh == null || units == Units.METRIC ? kmh : kmh * KMH_TO_MPH;
    }

    private static Double temperature(Double celsius, Units units) {
        return celsius == null || units == Units.METRIC ? celsius : celsius * 9.0 / 5.0 + 32.0;
    }
}
