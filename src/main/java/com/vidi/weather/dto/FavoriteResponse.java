package com.vidi.weather.dto;

import java.time.Instant;

public record FavoriteResponse(String city, Instant createdAt) {
}
