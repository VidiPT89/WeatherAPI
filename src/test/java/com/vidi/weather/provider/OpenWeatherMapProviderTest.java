package com.vidi.weather.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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

class OpenWeatherMapProviderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private OpenWeatherMapProvider provider;

    @BeforeEach
    void setUp() {
        provider = buildProvider(3000, 3000);
    }

    private OpenWeatherMapProvider buildProvider(int connectTimeoutMs, int readTimeoutMs) {
        WeatherApiProperties properties = new WeatherApiProperties(
                new WeatherApiProperties.OpenMeteo("unused", "unused"),
                new WeatherApiProperties.OpenWeatherMap(wireMock.baseUrl() + "/data/2.5/weather", "test-key"),
                new WeatherApiProperties.Cache(15, 500),
                new WeatherApiProperties.Http(connectTimeoutMs, readTimeoutMs));

        RestTemplate restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();

        return new OpenWeatherMapProvider(restTemplate, properties);
    }

    @Test
    void convertsKelvinAndMetersPerSecondToMetricUnits() {
        stubSuccess("""
                {"weather": [{"description": "clear sky"}],
                 "main": {"temp": 295.65, "feels_like": 294.5, "humidity": 60},
                 "wind": {"speed": 5.0},
                 "name": "Lisbon",
                 "sys": {"country": "PT"}}
                """);

        WeatherData result = provider.fetchCurrentWeather("Lisboa", Units.METRIC);

        assertThat(result.city()).isEqualTo("Lisbon");
        assertThat(result.country()).isEqualTo("PT");
        assertThat(result.temperature()).isCloseTo(22.5, within(0.01));
        assertThat(result.windSpeed()).isCloseTo(18.0, within(0.01));
        assertThat(result.description()).isEqualTo("clear sky");
        assertThat(result.provider()).isEqualTo("open-weather-map");
    }

    @Test
    void convertsKelvinAndMetersPerSecondToImperialUnits() {
        stubSuccess("""
                {"weather": [{"description": "clear sky"}],
                 "main": {"temp": 295.65, "feels_like": 294.5, "humidity": 60},
                 "wind": {"speed": 5.0},
                 "name": "Lisbon",
                 "sys": {"country": "PT"}}
                """);

        WeatherData result = provider.fetchCurrentWeather("Lisboa", Units.IMPERIAL);

        assertThat(result.temperature()).isCloseTo(72.5, within(0.1));
        assertThat(result.windSpeed()).isCloseTo(11.18, within(0.1));
    }

    @Test
    void throwsCityNotFound_whenApiReturns404() {
        wireMock.stubFor(get(urlPathEqualTo("/data/2.5/weather"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> provider.fetchCurrentWeather("Atlantis", Units.METRIC))
                .isInstanceOf(CityNotFoundException.class);
    }

    @Test
    void throwsProviderUnavailable_whenApiReturnsServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/data/2.5/weather"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> provider.fetchCurrentWeather("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void throwsProviderUnavailable_whenApiKeyIsInvalid() {
        wireMock.stubFor(get(urlPathEqualTo("/data/2.5/weather"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> provider.fetchCurrentWeather("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void throwsProviderQuotaExceeded_whenApiReturns429() {
        wireMock.stubFor(get(urlPathEqualTo("/data/2.5/weather"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> provider.fetchCurrentWeather("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderQuotaExceededException.class);
    }

    @Test
    void throwsProviderUnavailable_whenRequestTimesOut() {
        wireMock.stubFor(get(urlPathEqualTo("/data/2.5/weather"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000)));
        OpenWeatherMapProvider slowProvider = buildProvider(200, 200);

        assertThatThrownBy(() -> slowProvider.fetchCurrentWeather("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    private void stubSuccess(String responseBody) {
        wireMock.stubFor(get(urlPathEqualTo("/data/2.5/weather"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }
}
