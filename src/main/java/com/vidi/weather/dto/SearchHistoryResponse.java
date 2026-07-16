package com.vidi.weather.dto;

import java.time.Instant;

public record SearchHistoryResponse(String city, String units, Instant searchedAt) {
}
