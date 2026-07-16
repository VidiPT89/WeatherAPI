package com.vidi.weather.exception;

public class InvalidCredentialsException extends WeatherServiceException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
