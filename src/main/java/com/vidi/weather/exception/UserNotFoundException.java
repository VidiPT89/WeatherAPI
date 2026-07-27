package com.vidi.weather.exception;

public class UserNotFoundException extends WeatherServiceException {

    public UserNotFoundException(Long id) {
        super("User not found: '%d'".formatted(id));
    }
}
