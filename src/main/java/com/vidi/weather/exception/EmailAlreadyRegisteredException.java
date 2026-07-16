package com.vidi.weather.exception;

public class EmailAlreadyRegisteredException extends WeatherServiceException {

    public EmailAlreadyRegisteredException(String email) {
        super("Email already registered: '%s'".formatted(email));
    }
}
