package com.vidi.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidi.weather.dto.AuthResponse;
import com.vidi.weather.dto.LoginRequest;
import com.vidi.weather.dto.RefreshRequest;
import com.vidi.weather.dto.RegisterRequest;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.service.WeatherAggregatorService;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "rate-limit.requests-per-minute=3")
class AuthAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WeatherAggregatorService weatherAggregatorService;

    @Test
    void weatherEndpointRejectsRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenAccessProtectedEndpointWithIssuedToken() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        stubWeatherAggregator();

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void registeringWithInvalidPayloadReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("not-an-email", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registeringTheSameEmailTwiceReturnsConflict() throws Exception {
        String email = uniqueEmail();
        registerAndGetToken(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        String email = uniqueEmail();
        registerAndGetToken(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() throws Exception {
        String email = uniqueEmail();
        registerAndGetToken(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk());
    }

    @Test
    void registerReturnsARefreshTokenAlongsideTheAccessToken() throws Exception {
        AuthResponse response = registerAndGetAuthResponse(uniqueEmail());

        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void refreshingWithAValidRefreshTokenReturnsANewTokenPair() throws Exception {
        AuthResponse original = registerAndGetAuthResponse(uniqueEmail());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(original.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse refreshed = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(refreshed.token()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();
    }

    @Test
    void refreshingWithAnInvalidRefreshTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("not-a-real-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesTheRefreshTokenSoALaterRefreshFails() throws Exception {
        AuthResponse original = registerAndGetAuthResponse(uniqueEmail());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(original.refreshToken()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(original.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exceedingTheConfiguredRateLimitReturnsTooManyRequests() throws Exception {
        String token = registerAndGetToken(uniqueEmail());
        stubWeatherAggregator();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void statsEndpointRejectsRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statsEndpointReturnsAggregateCounts_whenAuthenticated() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        stubWeatherAggregator();

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalSearches").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.mostSearchedCity").isNotEmpty())
                .andExpect(jsonPath("$.mostSearchedCityCount").value(Matchers.greaterThanOrEqualTo(1)));
    }

    private void stubWeatherAggregator() {
        WeatherData sampleData = new WeatherData(
                "Lisboa", "Portugal", 22.5, 21.8, 65, 12.3, "Clear sky", Units.METRIC, "open-meteo", Instant.now());
        when(weatherAggregatorService.getCurrentWeather(any(), any()))
                .thenReturn(new WeatherResult(sampleData, false));
    }

    private String registerAndGetToken(String email) throws Exception {
        return registerAndGetAuthResponse(email).token();
    }

    private AuthResponse registerAndGetAuthResponse(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String uniqueEmail() {
        return "auth-%s@example.com".formatted(UUID.randomUUID());
    }
}
