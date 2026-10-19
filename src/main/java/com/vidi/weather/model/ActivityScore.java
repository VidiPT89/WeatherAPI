package com.vidi.weather.model;

import java.util.List;

/**
 * A 0..10 score for one activity plus the reason codes behind it (e.g. {@code WIND_OFFSHORE}),
 * which clients translate into their own language.
 */
public record ActivityScore(int score, List<String> reasons) {
}
