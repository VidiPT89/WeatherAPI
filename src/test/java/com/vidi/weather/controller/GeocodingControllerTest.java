package com.vidi.weather.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.GeocodingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GeocodingController.class)
class GeocodingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeocodingService geocodingService;

    private final AuthenticatedUser authenticatedUser =
            new AuthenticatedUser(new User("test@example.com", "hash", Units.METRIC));

    @Test
    void returns200WithSuggestions_whenQueryMatchesCities() throws Exception {
        when(geocodingService.searchCities(eq("Lis"), eq(5))).thenReturn(List.of(
                new GeocodingResult("Lisbon", "Portugal", 38.7167, -9.1333)));

        mockMvc.perform(get("/api/v1/geocoding").param("query", "Lis").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Lis"))
                .andExpect(jsonPath("$.results[0].name").value("Lisbon"))
                .andExpect(jsonPath("$.results[0].country").value("Portugal"));
    }

    @Test
    void returns200WithEmptyResults_whenNoCityMatches() throws Exception {
        when(geocodingService.searchCities(eq("Atlantis"), eq(5))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/geocoding").param("query", "Atlantis").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void returns400_whenQueryParamIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/geocoding").with(user(authenticatedUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400_whenQueryParamIsBlank() throws Exception {
        mockMvc.perform(get("/api/v1/geocoding").param("query", "   ").with(user(authenticatedUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clampsLimit_whenLimitExceedsMax() throws Exception {
        when(geocodingService.searchCities(eq("Lis"), eq(10))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/geocoding").param("query", "Lis").param("limit", "50").with(user(authenticatedUser)))
                .andExpect(status().isOk());
    }
}
