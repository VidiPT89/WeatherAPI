package com.vidi.weather.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidi.weather.dto.FavoriteRequest;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.FavoriteAlreadyExistsException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.FavoriteService;
import com.vidi.weather.service.SearchHistoryService;
import com.vidi.weather.service.WeatherAggregatorService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WeatherAggregatorService weatherAggregatorService;

    @MockitoBean
    private SearchHistoryService searchHistoryService;

    @MockitoBean
    private FavoriteService favoriteService;

    private final User principalUser = new User("test@example.com", "hash", Units.METRIC);
    private final AuthenticatedUser authenticatedUser = new AuthenticatedUser(principalUser);

    private final WeatherData sampleData = new WeatherData(
            "Lisboa", "Portugal", 22.5, 21.8, 65, 12.3, "Clear sky", Units.METRIC, "open-meteo", Instant.now());

    @Test
    void returns200WithNormalizedWeather_whenCityIsValid() throws Exception {
        when(weatherAggregatorService.getCurrentWeather(eq("Lisboa"), eq(Units.METRIC)))
                .thenReturn(new WeatherResult(sampleData, false));

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Lisboa"))
                .andExpect(jsonPath("$.units").value("metric"))
                .andExpect(jsonPath("$.fromCache").value(false));
    }

    @Test
    void usesUserPreferredUnits_whenUnitsParamIsOmitted() throws Exception {
        User imperialUser = new User("imperial@example.com", "hash", Units.IMPERIAL);
        when(weatherAggregatorService.getCurrentWeather(eq("Lisboa"), eq(Units.IMPERIAL)))
                .thenReturn(new WeatherResult(sampleData, false));

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa").with(user(new AuthenticatedUser(imperialUser))))
                .andExpect(status().isOk());
    }

    @Test
    void returns400_whenCityParamIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/weather").with(user(authenticatedUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400_whenCityParamIsBlank() throws Exception {
        mockMvc.perform(get("/api/v1/weather").param("city", "   ").with(user(authenticatedUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400_whenUnitsIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa").param("units", "kelvin").with(user(authenticatedUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404_whenCityNotFound() throws Exception {
        when(weatherAggregatorService.getCurrentWeather(any(), any()))
                .thenThrow(new CityNotFoundException("Atlantis"));

        mockMvc.perform(get("/api/v1/weather").param("city", "Atlantis").with(user(authenticatedUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns502_whenProviderIsUnavailable() throws Exception {
        when(weatherAggregatorService.getCurrentWeather(any(), any()))
                .thenThrow(new ProviderUnavailableException("open-meteo", new RuntimeException("boom")));

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa").with(user(authenticatedUser)))
                .andExpect(status().isBadGateway());
    }

    @Test
    void returns429_whenProviderQuotaExceeded() throws Exception {
        when(weatherAggregatorService.getCurrentWeather(any(), any()))
                .thenThrow(new ProviderQuotaExceededException("open-meteo"));

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa").with(user(authenticatedUser)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void returns201_whenFavoriteIsAdded() throws Exception {
        when(favoriteService.add(principalUser, "Lisboa"))
                .thenReturn(new com.vidi.weather.dto.FavoriteResponse("Lisboa", Instant.now()));

        mockMvc.perform(post("/api/v1/weather/favorites")
                        .with(user(authenticatedUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FavoriteRequest("Lisboa"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city").value("Lisboa"));
    }

    @Test
    void returns409_whenFavoriteAlreadyExists() throws Exception {
        when(favoriteService.add(principalUser, "Lisboa")).thenThrow(new FavoriteAlreadyExistsException("Lisboa"));

        mockMvc.perform(post("/api/v1/weather/favorites")
                        .with(user(authenticatedUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FavoriteRequest("Lisboa"))))
                .andExpect(status().isConflict());
    }

    @Test
    void returns200_whenListingHistory() throws Exception {
        when(searchHistoryService.listForUser(principalUser)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/weather/history").with(user(authenticatedUser)))
                .andExpect(status().isOk());
    }

    @Test
    void returns200_whenListingFavorites() throws Exception {
        when(favoriteService.listForUser(principalUser)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/weather/favorites").with(user(authenticatedUser)))
                .andExpect(status().isOk());
    }
}
