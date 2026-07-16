package com.vidi.weather.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vidi.weather.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
