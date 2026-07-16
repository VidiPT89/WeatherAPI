package com.vidi.weather.dto;

import jakarta.validation.constraints.NotBlank;

public record FavoriteRequest(@NotBlank String city) {
}
