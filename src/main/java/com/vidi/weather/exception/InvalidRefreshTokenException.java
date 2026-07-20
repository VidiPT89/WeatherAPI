package com.vidi.weather.exception;

public class InvalidRefreshTokenException extends WeatherServiceException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
