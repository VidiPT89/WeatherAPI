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
        "resilience4j.circuitbreaker.instances.open-weather-map.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.open-weather-map.sliding-window-size=3",
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
        registry.add("weather.open-weather-map.reverse-geocoding-url", () -> wireMock.baseUrl() + "/geo/1.0/reverse");
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
                .andExpect(jsonPath("$.provider").value("open-weather-map"));
    }

    @Test
    void fallsBackToSecondaryProvider_whenPrimaryFails() throws Exception {
        stubOpenWeatherMapFailure(500);
        stubOpenMeteoSuccess();

        mockMvc.perform(get("/api/v1/weather").param("city", "Porto").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("open-meteo"));
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailures_andStopsCallingPrimary() throws Exception {
        stubOpenWeatherMapFailure(500);
        stubOpenMeteoSuccess();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/weather").param("city", "CircuitCity" + i).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("open-weather-map").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        int hitsBeforeNextCall = wireMock.findAll(
                WireMock.getRequestedFor(WireMock.urlPathEqualTo("/data/2.5/weather"))).size();

        mockMvc.perform(get("/api/v1/weather").param("city", "CircuitCityAfterOpen").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("open-meteo"));

        int hitsAfterNextCall = wireMock.findAll(
                WireMock.getRequestedFor(WireMock.urlPathEqualTo("/data/2.5/weather"))).size();
        assertThat(hitsAfterNextCall)
                .as("the open circuit breaker should skip calling open-weather-map entirely")
                .isEqualTo(hitsBeforeNextCall);
    }

    @Test
    void nearbyWeather_usesCoordinatesDirectly_evenWhenReverseGeocodedNameWouldNotResolveByName() throws Exception {
        // The reverse-geocoded name ("Agualva-Cacém") is deliberately never stubbed on the
        // weather-by-name endpoint (?q=) below -- only the by-coordinates one (?lat=&lon=) is.
        // If /nearby round-tripped through the name instead of using the coordinates it already
        // has, this would fail with CITY_NOT_FOUND, reproducing the real production bug.
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/geo/1.0/reverse"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"name": "Agualva-Cacém", "country": "PT", "lat": 38.7629, "lon": -9.3025}]
                                """)));
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/data/2.5/weather"))
                .withQueryParam("lat", WireMock.equalTo("38.7629"))
                .withQueryParam("lon", WireMock.equalTo("-9.3025"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"weather": [{"description": "clear sky"}],
                                 "main": {"temp": 293.15, "feels_like": 292.0, "humidity": 55},
                                 "wind": {"speed": 3.0}, "name": "Agualva-Cacém", "sys": {"country": "PT"}}
                                """)));

        mockMvc.perform(get("/api/v1/weather/nearby")
                        .param("lat", "38.7629").param("lon", "-9.3025")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Agualva-Cacém"));
    }

    @Test
    void countryQualifiedCity_resolvesToTheMatchingCountry_notTheTopBareNameMatch() throws Exception {
        // "Beja" alone has same-named candidates in several countries; OpenWeatherMap's
        // weather-by-name endpoint only understands ISO country codes, not "Portugal" spelled
        // out, so forwarding "Beja, Portugal" to it verbatim would silently match a different
        // "Beja" (whichever OpenWeatherMap's own bare-name search ranks first) instead of failing
        // loudly or resolving the one actually tapped in the suggestion dropdown.
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/geo/v1/search"))
                .withQueryParam("name", WireMock.equalTo("Beja"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"results": [
                                    {"name": "Beja", "country": "Tunisia", "latitude": 36.72564, "longitude": 9.18169},
                                    {"name": "Beja", "country": "Portugal", "latitude": 38.01469, "longitude": -7.86284}
                                ]}
                                """)));
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/data/2.5/weather"))
                .withQueryParam("lat", WireMock.equalTo("38.01469"))
                .withQueryParam("lon", WireMock.equalTo("-7.86284"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"weather": [{"description": "overcast clouds"}],
                                 "main": {"temp": 288.7, "feels_like": 288.75, "humidity": 94},
                                 "wind": {"speed": 4.63}, "name": "Beja", "sys": {"country": "PT"}}
                                """)));

        mockMvc.perform(get("/api/v1/weather").param("city", "Beja, Portugal").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Beja"))
                .andExpect(jsonPath("$.country").value("PT"));
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

    private void stubOpenWeatherMapFailure(int status) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/data/2.5/weather"))
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
