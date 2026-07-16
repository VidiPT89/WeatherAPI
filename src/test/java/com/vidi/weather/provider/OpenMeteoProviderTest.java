package com.vidi.weather.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vidi.weather.config.WeatherApiProperties;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class OpenMeteoProviderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private OpenMeteoProvider provider;

    @BeforeEach
    void setUp() {
        provider = buildProvider(3000, 3000);
    }

    private OpenMeteoProvider buildProvider(int connectTimeoutMs, int readTimeoutMs) {
        WeatherApiProperties properties = new WeatherApiProperties(
                new WeatherApiProperties.OpenMeteo(
                        wireMock.baseUrl() + "/v1/search",
                        wireMock.baseUrl() + "/v1/forecast"),
                new WeatherApiProperties.Cache(15, 500),
                new WeatherApiProperties.Http(connectTimeoutMs, readTimeoutMs));

        RestTemplate restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();

        return new OpenMeteoProvider(restTemplate, properties);
    }

    @Test
    void returnsNormalizedWeatherData_whenProviderRespondsSuccessfully() {
        stubGeocoding("Lisboa", """
                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333}]}
                """);
        stubForecast("""
                {"current": {"temperature_2m": 22.5, "relative_humidity_2m": 65, "apparent_temperature": 21.8, "wind_speed_10m": 12.3, "weather_code": 1}}
                """);

        WeatherData result = provider.fetchCurrentWeather("Lisboa", Units.METRIC);

        assertThat(result.city()).isEqualTo("Lisbon");
        assertThat(result.country()).isEqualTo("Portugal");
        assertThat(result.temperature()).isEqualTo(22.5);
        assertThat(result.feelsLike()).isEqualTo(21.8);
        assertThat(result.humidity()).isEqualTo(65);
        assertThat(result.windSpeed()).isEqualTo(12.3);
        assertThat(result.description()).isEqualTo("Mainly clear");
        assertThat(result.provider()).isEqualTo("open-meteo");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/v1/search"))
                .withQueryParam("name", equalTo("Lisboa")));
    }

    @Test
    void throwsCityNotFound_whenGeocodingReturnsNoResults() {
        stubGeocoding("Atlantis", """
                {"results": []}
                """);

        assertThatThrownBy(() -> provider.fetchCurrentWeather("Atlantis", Units.METRIC))
                .isInstanceOf(CityNotFoundException.class);
    }

    @Test
    void throwsProviderUnavailable_whenGeocodingReturnsServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> provider.fetchCurrentWeather("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void throwsProviderQuotaExceeded_whenGeocodingReturnsTooManyRequests() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> provider.fetchCurrentWeather("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderQuotaExceededException.class);
    }

    @Test
    void throwsProviderUnavailable_whenGeocodingTimesOut() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000)));
        OpenMeteoProvider slowProvider = buildProvider(200, 200);

        assertThatThrownBy(() -> slowProvider.fetchCurrentWeather("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    private void stubGeocoding(String city, String responseBody) {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .withQueryParam("name", equalTo(city))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    private void stubForecast(String responseBody) {
        wireMock.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }
}
