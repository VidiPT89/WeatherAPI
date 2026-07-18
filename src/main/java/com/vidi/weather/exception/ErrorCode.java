package com.vidi.weather.exception;

/**
 * Stable, language-agnostic identifier for each error case the API can return.
 * Clients localize on this instead of parsing the English {@code message}, which
 * for validation failures is dynamic (field name + Bean Validation's own text).
 */
public enum ErrorCode {
    CITY_NOT_FOUND,
    PROVIDER_UNAVAILABLE,
    PROVIDER_QUOTA_EXCEEDED,
    VALIDATION_FAILED,
    EMAIL_ALREADY_REGISTERED,
    INVALID_CREDENTIALS,
    FAVORITE_ALREADY_EXISTS,
    UNAUTHENTICATED,
    ACCESS_DENIED,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
