package com.vidi.weather.exception;

public class ProviderUnavailableException extends WeatherServiceException {

    public ProviderUnavailableException(String providerName, Throwable cause) {
        super("Weather provider '%s' is currently unavailable".formatted(providerName), cause);
    }
}
