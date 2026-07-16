package com.vidi.weather.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.service.WeatherAggregatorService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherAggregatorService weatherAggregatorService;

    private final WeatherData sampleData = new WeatherData(
            "Lisboa", "Portugal", 22.5, 21.8, 65, 12.3, "Clear sky", Units.METRIC, "open-meteo", Instant.now());

    @Test
    void returns200WithNormalizedWeather_whenCityIsValid() throws Exception {
        when(weatherAggregatorService.getCurrentWeather(eq("Lisboa"), eq(Units.METRIC)))
                .thenReturn(new WeatherResult(sampleData, false));

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Lisboa"))
                .andExpect(jsonPath("$.units").value("metric"))
                .andExpect(jsonPath("$.fromCache").value(false));
    }

    @Test
    void returns400_whenCityParamIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/weather"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400_whenCityParamIsBlank() throws Exception {
        mockMvc.perform(get("/api/v1/weather").param("city", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400_whenUnitsIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa").param("units", "kelvin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404_whenCityNotFound() throws Exception {
        when(weatherAggregatorService.getCurrentWeather(any(), any()))
                .thenThrow(new CityNotFoundException("Atlantis"));

        mockMvc.perform(get("/api/v1/weather").param("city", "Atlantis"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns502_whenProviderIsUnavailable() throws Exception {
        when(weatherAggregatorService.getCurrentWeather(any(), any()))
                .thenThrow(new ProviderUnavailableException("open-meteo", new RuntimeException("boom")));

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void returns429_whenProviderQuotaExceeded() throws Exception {
        when(weatherAggregatorService.getCurrentWeather(any(), any()))
                .thenThrow(new ProviderQuotaExceededException("open-meteo"));

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa"))
                .andExpect(status().isTooManyRequests());
    }
}
