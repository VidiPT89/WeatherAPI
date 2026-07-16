package com.vidi.weather.dto;

import jakarta.validation.constraints.NotBlank;

public record UserPreferences(@NotBlank String units) {
}
