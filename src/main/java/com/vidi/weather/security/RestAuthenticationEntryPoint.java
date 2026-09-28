package com.vidi.weather.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidi.weather.dto.ErrorResponse;
import com.vidi.weather.exception.ErrorCode;
import com.vidi.weather.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        boolean databaseUnavailable = Boolean.TRUE.equals(
                request.getAttribute(JwtAuthenticationFilter.DATABASE_UNAVAILABLE_ATTRIBUTE));
        HttpStatus status = databaseUnavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED;
        ErrorResponse body = databaseUnavailable
                ? ErrorResponse.of(
                        status.value(),
                        status.getReasonPhrase(),
                        GlobalExceptionHandler.DATABASE_UNAVAILABLE_MESSAGE,
                        request.getRequestURI(),
                        ErrorCode.DATABASE_UNAVAILABLE)
                : ErrorResponse.of(
                        status.value(),
                        status.getReasonPhrase(),
                        "Authentication is required to access this resource.",
                        request.getRequestURI(),
                        ErrorCode.UNAUTHENTICATED);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
