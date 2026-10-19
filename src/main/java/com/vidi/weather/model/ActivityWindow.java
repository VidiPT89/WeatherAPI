package com.vidi.weather.model;

import java.time.LocalDateTime;
import java.util.List;

/** The best few daylight hours of a day for one activity; {@code end} is exclusive. */
public record ActivityWindow(LocalDateTime start, LocalDateTime end, int score, List<String> reasons) {
}
