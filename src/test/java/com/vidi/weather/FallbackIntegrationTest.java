package com.vidi.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.vidi.weather.dto.AuthResponse;
import com.vidi.weather.dto.RegisterRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.open-meteo.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.open-meteo.sliding-window-size=3",
        "resilience4j.retry.instances.open-meteo.wait-duration=10ms",
        "resilience4j.retry.instances.open-weather-map.wait-duration=10ms"
})
class FallbackIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerProviderUrls(DynamicPropertyRegistry registry) {
        registry.add("weather.open-meteo.geocoding-url", () -> wireMock.baseUrl() + "/geo/v1/search");
        registry.add("weather.open-meteo.forecast-url", () -> wireMock.baseUrl() + "/v1/forecast");
        registry.add("weather.open-weather-map.base-url", () -> wireMock.baseUrl() + "/data/2.5/weather");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        circuitBreakerRegistry.circuitBreaker("open-meteo").reset();
        circuitBreakerRegistry.circuitBreaker("open-weather-map").reset();
        wireMock.resetAll();
        token = registerAndGetToken();
    }

    @Test
    void usesPrimaryProvider_whenItSucceeds() throws Exception {
        stubOpenMeteoSuccess();
        stubOpenWeatherMapSuccess();

        mockMvc.perform(get("/api/v1/weather").param("city", "Lisboa").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("open-meteo"));
    }

    @Test
    void fallsBackToSecondaryProvider_whenPrimaryFails() throws Exception {
        stubOpenMeteoFailure(500);
        stubOpenWeatherMapSuccess();

        mockMvc.perform(get("/api/v1/weather").param("city", "Porto").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("open-weather-map"));
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailures_andStopsCallingPrimary() throws Exception {
        stubOpenMeteoFailure(500);
        stubOpenWeatherMapSuccess();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/weather").param("city", "CircuitCity" + i).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("open-meteo").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        int hitsBeforeNextCall = wireMock.findAll(
                WireMock.getRequestedFor(WireMock.urlPathEqualTo("/geo/v1/search"))).size();

        mockMvc.perform(get("/api/v1/weather").param("city", "CircuitCityAfterOpen").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("open-weather-map"));

        int hitsAfterNextCall = wireMock.findAll(
                WireMock.getRequestedFor(WireMock.urlPathEqualTo("/geo/v1/search"))).size();
        assertThat(hitsAfterNextCall)
                .as("the open circuit breaker should skip calling open-meteo entirely")
                .isEqualTo(hitsBeforeNextCall);
    }

    @Test
    void compareEndpointReturnsBothProvidersSideBySide() throws Exception {
        stubOpenMeteoSuccess();
        stubOpenWeatherMapSuccess();

        mockMvc.perform(get("/api/v1/weather/compare").param("city", "Lisboa").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].provider").value("open-meteo"))
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[1].provider").value("open-weather-map"))
                .andExpect(jsonPath("$.results[1].success").value(true));
    }

    @Test
    void compareEndpointReportsPerProviderFailure_withoutFailingTheWholeRequest() throws Exception {
        stubOpenMeteoFailure(500);
        stubOpenWeatherMapSuccess();

        mockMvc.perform(get("/api/v1/weather/compare").param("city", "Lisboa").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].provider").value("open-meteo"))
                .andExpect(jsonPath("$.results[0].success").value(false))
                .andExpect(jsonPath("$.results[0].errorMessage").value("Provider unavailable"))
                .andExpect(jsonPath("$.results[1].success").value(true));
    }

    private void stubOpenMeteoSuccess() {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/geo/v1/search"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333}]}
                                """)));
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/forecast"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"current": {"temperature_2m": 20.0, "relative_humidity_2m": 60, "apparent_temperature": 19.5, "wind_speed_10m": 10.0, "weather_code": 0}}
                                """)));
    }

    private void stubOpenMeteoFailure(int status) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/geo/v1/search"))
                .willReturn(WireMock.aResponse().withStatus(status)));
    }

    private void stubOpenWeatherMapSuccess() {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/data/2.5/weather"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"weather": [{"description": "clear sky"}],
                                 "main": {"temp": 293.15, "feels_like": 292.0, "humidity": 55},
                                 "wind": {"speed": 3.0}, "name": "Lisbon", "sys": {"country": "PT"}}
                                """)));
    }

    private String registerAndGetToken() throws Exception {
        String email = "fallback-%s@example.com".formatted(UUID.randomUUID());
        var result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return response.token();
    }
}
