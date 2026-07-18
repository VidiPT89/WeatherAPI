package com.vidi.weather.model;

import java.time.LocalDateTime;

public record HourlyForecast(
        LocalDateTime time,
        double temperature,
        String description,
        int precipitationProbability
) {
}
