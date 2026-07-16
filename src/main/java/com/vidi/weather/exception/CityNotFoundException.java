package com.vidi.weather.exception;

public class CityNotFoundException extends WeatherServiceException {

    public CityNotFoundException(String city) {
        super("City not found: '%s'".formatted(city));
    }
}
