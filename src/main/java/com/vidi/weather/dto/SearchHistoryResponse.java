package com.vidi.weather.dto;

import java.time.Instant;

public record SearchHistoryResponse(Long id, String city, String units, Instant searchedAt) {
}
