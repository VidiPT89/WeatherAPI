package com.vidi.weather.exception;

public class FavoriteAlreadyExistsException extends WeatherServiceException {

    public FavoriteAlreadyExistsException(String city) {
        super("City is already a favorite: '%s'".formatted(city));
    }
}
