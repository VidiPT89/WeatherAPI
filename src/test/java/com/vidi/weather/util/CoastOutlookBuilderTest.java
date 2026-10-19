package com.vidi.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidi.weather.model.CoastDay;
import com.vidi.weather.model.CoastHour;
import com.vidi.weather.model.CoastOutlookData;
import com.vidi.weather.model.CoastSpot;
import com.vidi.weather.provider.openmeteo.CoastMarineResponse;
import com.vidi.weather.provider.openmeteo.CoastWindResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoastOutlookBuilderTest {

    private static final CoastSpot WEST_FACING = new CoastSpot("test", "Test", "Area", 38.9, -9.4, 270);

    /** Two days, 48 hours, with a tide curve peaking at 06:00 and 18:00 and an offshore window at 10-12h. */
    private static CoastMarineResponse sea() {
        List<String> times = new ArrayList<>();
        List<Double> height = new ArrayList<>();
        List<Double> period = new ArrayList<>();
        List<Double> direction = new ArrayList<>();
        List<Double> seaLevel = new ArrayList<>();
        for (int h = 0; h < 48; h++) {
            times.add(LocalDateTime.of(2026, 10, 20, 0, 0).plusHours(h).toString());
            height.add(1.5);
            period.add(12.0);
            direction.add(290.0);
            seaLevel.add(Math.cos((h - 6) * Math.PI / 6));
        }
        return new CoastMarineResponse(new CoastMarineResponse.Hourly(
                times, height, period, direction, height, period, direction,
                Collections.nCopies(48, 17.0), seaLevel));
    }

    private static CoastWindResponse wind() {
        List<String> times = new ArrayList<>();
        List<Double> speed = new ArrayList<>();
        List<Double> windFrom = new ArrayList<>();
        for (int h = 0; h < 48; h++) {
            int hourOfDay = h % 24;
            times.add(LocalDateTime.of(2026, 10, 20, 0, 0).plusHours(h).toString());
            boolean offshore = hourOfDay >= 10 && hourOfDay < 13;
            speed.add(offshore ? 10.0 : 22.0);
            windFrom.add(offshore ? 90.0 : 270.0);
        }
        return new CoastWindResponse(
                new CoastWindResponse.Hourly(times, speed, windFrom, speed,
                        Collections.nCopies(48, 20.0), Collections.nCopies(48, 0)),
                new CoastWindResponse.Daily(
                        List.of("2026-10-20", "2026-10-21"),
                        List.of("2026-10-20T07:40", "2026-10-21T07:41"),
                        List.of("2026-10-20T18:50", "2026-10-21T18:48")),
                3600);
    }

    @Test
    void buildsOneHourPerSeaReading_withWindMatchedByTime() {
        CoastOutlookData data = CoastOutlookBuilder.build(
                WEST_FACING, "open-meteo", sea(), wind(), LocalDateTime.of(2026, 10, 20, 0, 30));

        assertThat(data.hours()).hasSize(48);
        CoastHour tenAm = data.hours().get(10);
        assertThat(tenAm.windExposure()).isEqualTo("offshore");
        assertThat(tenAm.windSpeedKmh()).isEqualTo(10.0);
        assertThat(data.hours().get(15).windExposure()).isEqualTo("onshore");
        assertThat(data.utcOffsetSeconds()).isEqualTo(3600);
    }

    @Test
    void beachScoreIsOnlyGivenInDaylight() {
        CoastOutlookData data = CoastOutlookBuilder.build(
                WEST_FACING, "open-meteo", sea(), wind(), LocalDateTime.of(2026, 10, 20, 0, 30));

        assertThat(data.hours().get(3).beachScore()).isNull();
        assertThat(data.hours().get(7).beachScore()).isNotNull();   // 07:00-08:00 includes sunrise
        assertThat(data.hours().get(19).beachScore()).isNull();
    }

    @Test
    void detectsTideTurnsAcrossTheWholeSeries() {
        CoastOutlookData data = CoastOutlookBuilder.build(
                WEST_FACING, "open-meteo", sea(), wind(), LocalDateTime.of(2026, 10, 20, 0, 30));

        assertThat(data.tideEvents()).extracting(e -> e.time())
                .contains("2026-10-20T06:00", "2026-10-20T12:00", "2026-10-20T18:00", "2026-10-21T06:00");
    }

    @Test
    void picksTheOffshoreHoursAsTheBestSurfWindow() {
        CoastOutlookData data = CoastOutlookBuilder.build(
                WEST_FACING, "open-meteo", sea(), wind(), LocalDateTime.of(2026, 10, 20, 0, 30));

        CoastDay today = data.days().get(0);
        assertThat(today.date()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(today.surf().start()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0));
        assertThat(today.surf().end()).isEqualTo(LocalDateTime.of(2026, 10, 20, 13, 0));
        assertThat(today.surf().score()).isEqualTo(10);
        assertThat(today.surf().reasons()).contains("WIND_OFFSHORE");
    }

    @Test
    void ignoresHoursThatHavePassed_whenPickingTodaysWindow() {
        CoastOutlookData data = CoastOutlookBuilder.build(
                WEST_FACING, "open-meteo", sea(), wind(), LocalDateTime.of(2026, 10, 20, 14, 10));

        CoastDay today = data.days().get(0);
        assertThat(today.surf().start()).isAfterOrEqualTo(LocalDateTime.of(2026, 10, 20, 14, 0));
        CoastDay tomorrow = data.days().get(1);
        assertThat(tomorrow.surf().start()).isEqualTo(LocalDateTime.of(2026, 10, 21, 10, 0));
    }

    @Test
    void leavesTheWindowsEmpty_onceTheDaylightIsOver() {
        CoastOutlookData data = CoastOutlookBuilder.build(
                WEST_FACING, "open-meteo", sea(), wind(), LocalDateTime.of(2026, 10, 20, 21, 0));

        CoastDay today = data.days().get(0);
        assertThat(today.surf()).isNull();
        assertThat(today.beach()).isNull();
    }
}
