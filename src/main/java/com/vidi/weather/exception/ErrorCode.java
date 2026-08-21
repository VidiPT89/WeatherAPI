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
    INVALID_REFRESH_TOKEN,
    OAUTH_TOKEN_INVALID,
    FAVORITE_ALREADY_EXISTS,
    FAVORITE_NOT_FOUND,
    SEARCH_HISTORY_ENTRY_NOT_FOUND,
    USER_NOT_FOUND,
    CONFLICT,
    UNAUTHENTICATED,
    ACCESS_DENIED,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
