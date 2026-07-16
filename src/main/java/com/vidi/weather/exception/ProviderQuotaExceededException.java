package com.vidi.weather.exception;

public class ProviderQuotaExceededException extends WeatherServiceException {

    public ProviderQuotaExceededException(String providerName) {
        super("Quota exceeded for weather provider '%s'".formatted(providerName));
    }
}
