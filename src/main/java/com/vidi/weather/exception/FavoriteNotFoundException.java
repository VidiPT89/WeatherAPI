package com.vidi.weather.exception;

public class FavoriteNotFoundException extends WeatherServiceException {

    public FavoriteNotFoundException(String city) {
        super("City is not a favorite: '%s'".formatted(city));
    }
}
