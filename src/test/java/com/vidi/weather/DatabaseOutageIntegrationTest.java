package com.vidi.weather;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.security.JwtService;
import com.vidi.weather.service.WeatherAggregatorService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the whole application against a database that doesn't exist (nothing listens on port 1)
 * to prove an outage only takes down what actually needs the database. This happened for real
 * when the free-tier Postgres ran out of compute quota: the app refused to start at all, so even
 * anonymous weather lookups -- which never touch the database -- were down for days.
 *
 * <p>Schema validation is switched off here because there is no schema to validate against.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unreachable",
        "spring.datasource.hikari.connection-timeout=250",
        "spring.jpa.hibernate.ddl-auto=none"
})
class DatabaseOutageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private WeatherAggregatorService weatherAggregatorService;

    private String validToken;

    @BeforeEach
    void setUp() {
        WeatherData lisboa = new WeatherData(
                "Lisboa", "Portugal", 22.5, 21.8, 65, 12.3, "Clear sky", Units.METRIC, "open-meteo", Instant.now());
        when(weatherAggregatorService.getCurrentWeather(eq("Lisboa"), eq(Units.METRIC)))
                .thenReturn(new WeatherResult(lisboa, false));
        validToken = jwtService.generateToken(new User("signed-in@example.com", "hash", Units.METRIC));
    }

    @Test
    void anonymousWeatherLookupStillWorks() throws Exception {
        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Lisboa"));
    }

    @Test
    void weatherLookupWithASessionStillWorks_servedAnonymously() throws Exception {
        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Lisboa"));
    }

    @Test
    void protectedEndpointAnswers503_notA401ThatWouldEndTheClientsSession() throws Exception {
        mockMvc.perform(get("/api/v1/weather/history")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_UNAVAILABLE"));
    }

    @Test
    void healthReportsTheDatabaseAsDown() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.database").value("DOWN"));
    }

    @Test
    void loginAnswers503_notInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"signed-in@example.com\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_UNAVAILABLE"));
    }
}
