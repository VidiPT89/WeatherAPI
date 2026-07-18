package com.vidi.weather.dto;

import com.vidi.weather.exception.ErrorCode;
import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        ErrorCode errorCode
) {
    public static ErrorResponse of(int status, String error, String message, String path, ErrorCode errorCode) {
        return new ErrorResponse(Instant.now(), status, error, message, path, errorCode);
    }
}
