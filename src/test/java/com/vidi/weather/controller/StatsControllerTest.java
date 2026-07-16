package com.vidi.weather.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vidi.weather.dto.StatsResponse;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    private final AuthenticatedUser authenticatedUser =
            new AuthenticatedUser(new User("test@example.com", "hash", Units.METRIC));

    @Test
    void returnsAggregateStats() throws Exception {
        when(statsService.getStats()).thenReturn(new StatsResponse(
                5, 42, 7, "Lisboa", 10, new StatsResponse.CacheStats(30, 12, 0.71)));

        mockMvc.perform(get("/api/v1/stats").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(5))
                .andExpect(jsonPath("$.totalSearches").value(42))
                .andExpect(jsonPath("$.totalFavorites").value(7))
                .andExpect(jsonPath("$.mostSearchedCity").value("Lisboa"))
                .andExpect(jsonPath("$.mostSearchedCityCount").value(10))
                .andExpect(jsonPath("$.cache.hitCount").value(30))
                .andExpect(jsonPath("$.cache.missCount").value(12));
    }
}
