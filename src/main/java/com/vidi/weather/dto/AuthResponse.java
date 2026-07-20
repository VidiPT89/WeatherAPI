package com.vidi.weather.dto;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, String refreshToken) {

    public static AuthResponse bearer(String token, long expiresInSeconds, String refreshToken) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, refreshToken);
    }
}
