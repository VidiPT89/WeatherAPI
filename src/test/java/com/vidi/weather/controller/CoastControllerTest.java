package com.vidi.weather.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vidi.weather.entity.User;
import com.vidi.weather.model.CoastHour;
import com.vidi.weather.model.CoastOutlookData;
import com.vidi.weather.model.CoastOutlookResult;
import com.vidi.weather.model.CoastSpot;
import com.vidi.weather.model.Units;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.CoastOutlookService;
import com.vidi.weather.service.CoastSpotCatalog;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CoastController.class)
@Import(CoastSpotCatalog.class)
class CoastControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoastOutlookService outlookService;

    private final AuthenticatedUser authenticatedUser =
            new AuthenticatedUser(new User("test@example.com", "hash", Units.METRIC));

    private static CoastOutlookResult outlookFor(CoastSpot spot) {
        CoastHour hour = new CoastHour(LocalDateTime.of(2026, 10, 20, 10, 0),
                1.5, 12.0, 290.0, 1.4, 13.0, 290.0, 10.0, 18.0, 90.0, "offshore",
                0.8, 17.0, 20.0, 0, 10, 7, 8);
        return new CoastOutlookResult(
                new CoastOutlookData(spot, "open-meteo", 3600, List.of(hour), List.of(), List.of()), false);
    }

    @Test
    void listsSpotsNearestFirst_withTheirDistance() throws Exception {
        mockMvc.perform(get("/api/v1/coast/spots").param("lat", "38.6979").param("lon", "-9.4215")
                        .with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("guincho"))
                .andExpect(jsonPath("$[0].distanceKm").isNumber());
    }

    @Test
    void listsAllSpotsWithoutDistance_whenNoPositionIsGiven() throws Exception {
        mockMvc.perform(get("/api/v1/coast/spots").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("viana-cabedelo"))
                .andExpect(jsonPath("$[0].distanceKm").doesNotExist());
    }

    @Test
    void returnsTheOutlookForAKnownSpot() throws Exception {
        when(outlookService.getOutlook(argThat(spot -> "guincho".equals(spot.id()))))
                .thenAnswer(invocation -> outlookFor(invocation.getArgument(0)));

        mockMvc.perform(get("/api/v1/coast/outlook").param("spot", "guincho").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spot.name").value("Guincho"))
                .andExpect(jsonPath("$.spot.facingDegrees").value(275))
                .andExpect(jsonPath("$.units").value("metric"))
                .andExpect(jsonPath("$.hours[0].windExposure").value("offshore"))
                .andExpect(jsonPath("$.hours[0].windSpeed").value(10.0))
                .andExpect(jsonPath("$.hours[0].surfScore").value(10));
    }

    @Test
    void convertsWindAndTemperatures_butKeepsWavesInMeters_forImperial() throws Exception {
        when(outlookService.getOutlook(any())).thenAnswer(invocation -> outlookFor(invocation.getArgument(0)));

        mockMvc.perform(get("/api/v1/coast/outlook").param("spot", "guincho").param("units", "imperial")
                        .with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours[0].windSpeed").value(org.hamcrest.Matchers.closeTo(6.21, 0.01)))
                .andExpect(jsonPath("$.hours[0].airTemperature").value(68.0))
                .andExpect(jsonPath("$.hours[0].waveHeightMeters").value(1.5));
    }

    @Test
    void returnsTheOutlookForAnyPoint_withoutABeachOrientation() throws Exception {
        when(outlookService.getOutlook(any())).thenAnswer(invocation -> outlookFor(invocation.getArgument(0)));

        mockMvc.perform(get("/api/v1/coast/outlook").param("lat", "38.7").param("lon", "-9.4")
                        .param("name", "Cascais, Portugal").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spot.name").value("Cascais, Portugal"))
                .andExpect(jsonPath("$.spot.facingDegrees").doesNotExist());
    }

    @Test
    void returns404_forAnUnknownSpot() throws Exception {
        mockMvc.perform(get("/api/v1/coast/outlook").param("spot", "atlantis").with(user(authenticatedUser)))
                .andExpect(status().isNotFound());
        verify(outlookService, never()).getOutlook(any());
    }

    @Test
    void returns400_withoutASpotOrCoordinates() throws Exception {
        mockMvc.perform(get("/api/v1/coast/outlook").with(user(authenticatedUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/coast/outlook").param("lat", "95").param("lon", "0").with(user(authenticatedUser)))
                .andExpect(status().isBadRequest());
    }
}
