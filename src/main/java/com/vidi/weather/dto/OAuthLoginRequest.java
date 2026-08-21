package com.vidi.weather.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(@NotBlank String idToken) {
}
