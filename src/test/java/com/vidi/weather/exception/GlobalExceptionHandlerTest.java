package com.vidi.weather.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vidi.weather.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesGenericWeatherServiceExceptionAsInternalServerError() {
        when(request.getRequestURI()).thenReturn("/api/v1/weather");

        ResponseEntity<ErrorResponse> response =
                handler.handleWeatherServiceException(new WeatherServiceException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred while fetching weather data.");
    }

    @Test
    void handlesUnexpectedExceptionAsInternalServerError() {
        when(request.getRequestURI()).thenReturn("/api/v1/weather");

        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred.");
    }

    @Test
    void handlesMalformedJsonBodyAsBadRequest_insteadOfInternalServerError() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        ResponseEntity<ErrorResponse> response =
                handler.handleMalformedBody(new HttpMessageNotReadableException("bad json"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void handlesWrongParameterTypeAsBadRequest_insteadOfInternalServerError() {
        when(request.getRequestURI()).thenReturn("/api/v1/geocoding");
        MethodParameter parameter = org.mockito.Mockito.mock(MethodParameter.class);
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Integer.class, "limit", parameter, null);

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("limit");
    }

    @Test
    void handlesUnmappedDataIntegrityViolationAsConflict_insteadOfInternalServerError() {
        when(request.getRequestURI()).thenReturn("/api/v1/weather/favorites");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.CONFLICT);
    }
}
