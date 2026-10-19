package com.vidi.weather.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A day's daylight and its best window per activity; a window is {@code null} when no hour scored. */
public record CoastDay(
        LocalDate date,
        LocalDateTime sunrise,
        LocalDateTime sunset,
        ActivityWindow surf,
        ActivityWindow fishing,
        ActivityWindow beach
) {
}
