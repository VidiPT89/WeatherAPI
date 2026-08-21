package com.vidi.weather.exception;

import com.vidi.weather.model.OAuthProvider;

public class OAuthTokenInvalidException extends WeatherServiceException {

    public OAuthTokenInvalidException(OAuthProvider provider) {
        super("Invalid or expired %s ID token".formatted(provider));
    }

    public OAuthTokenInvalidException(OAuthProvider provider, Throwable cause) {
        super("Invalid or expired %s ID token".formatted(provider), cause);
    }
}
