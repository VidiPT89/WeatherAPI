package com.vidi.weather.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
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
import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.MarineData;
import com.vidi.weather.model.TideEvent;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
                        wireMock.baseUrl() + "/v1/forecast",
                        wireMock.baseUrl() + "/v1/marine"),
                new WeatherApiProperties.OpenWeatherMap("unused", "unused"),
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
                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333, "population": 517802}]}
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
    void trustsPrimaryTopResult_whenItHasPopulationData_withoutQueryingLocalizedLanguage() {
        stubGeocoding("Paris", """
                {"results": [{"name": "Paris", "country": "France", "latitude": 48.8566, "longitude": 2.3522, "population": 2138551}]}
                """);
        stubForecast("""
                {"current": {"temperature_2m": 18.0, "relative_humidity_2m": 60, "apparent_temperature": 17.5, "wind_speed_10m": 10.0, "weather_code": 0}}
                """);

        WeatherData result = provider.fetchCurrentWeather("Paris", Units.METRIC);

        assertThat(result.city()).isEqualTo("Paris");
        assertThat(result.country()).isEqualTo("France");
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/v1/search")));
    }

    @Test
    void retriesWithPortugueseLocale_whenPrimaryResultsHaveNoPopulationData() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .withQueryParam("name", equalTo("Lisboa"))
                .withQueryParam("language", absent())
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"results": [{"name": "Lisboa", "country": "Mozambique", "latitude": -20.12, "longitude": 33.77}]}
                                """)));
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .withQueryParam("name", equalTo("Lisboa"))
                .withQueryParam("language", equalTo("pt"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"results": [{"name": "Lisboa", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333, "population": 517802}]}
                                """)));
        stubForecast("""
                {"current": {"temperature_2m": 22.5, "relative_humidity_2m": 65, "apparent_temperature": 21.8, "wind_speed_10m": 12.3, "weather_code": 1}}
                """);

        WeatherData result = provider.fetchCurrentWeather("Lisboa", Units.METRIC);

        assertThat(result.city()).isEqualTo("Lisboa");
        assertThat(result.country()).isEqualTo("Portugal");
    }

    @Test
    void fallsBackToFirstPrimaryResult_whenNoCandidateHasPopulationData() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .withQueryParam("name", equalTo("Smalltown"))
                .withQueryParam("language", absent())
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"results": [{"name": "Smalltown", "country": "Nowhere", "latitude": 1.0, "longitude": 2.0}]}
                                """)));
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .withQueryParam("name", equalTo("Smalltown"))
                .withQueryParam("language", equalTo("pt"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"results": []}
                                """)));
        stubForecast("""
                {"current": {"temperature_2m": 5.0, "relative_humidity_2m": 80, "apparent_temperature": 3.0, "wind_speed_10m": 20.0, "weather_code": 3}}
                """);

        WeatherData result = provider.fetchCurrentWeather("Smalltown", Units.METRIC);

        assertThat(result.city()).isEqualTo("Smalltown");
        assertThat(result.country()).isEqualTo("Nowhere");
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

    @Test
    void returnsHourlyAndDailyForecast_whenProviderRespondsSuccessfully() {
        stubGeocoding("Lisboa", """
                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333, "population": 517802}]}
                """);
        stubForecastSeries("""
                {
                  "hourly": {
                    "time": ["2024-01-01T00:00", "2024-01-01T01:00"],
                    "temperature_2m": [12.5, 11.9],
                    "weather_code": [1, 2],
                    "precipitation_probability": [10, 20]
                  },
                  "daily": {
                    "time": ["2024-01-01", "2024-01-02"],
                    "temperature_2m_max": [15.0, 16.2],
                    "temperature_2m_min": [8.1, 9.0],
                    "weather_code": [1, 3],
                    "sunrise": ["2024-01-01T07:45", "2024-01-02T07:45"],
                    "sunset": ["2024-01-01T17:30", "2024-01-02T17:31"],
                    "uv_index_max": [3.5, 3.8],
                    "precipitation_probability_max": [20, 30]
                  }
                }
                """);

        ForecastData result = provider.fetchForecast("Lisboa", Units.METRIC);

        assertThat(result.city()).isEqualTo("Lisbon");
        assertThat(result.country()).isEqualTo("Portugal");
        assertThat(result.hourly()).hasSize(2);
        assertThat(result.hourly().get(0).time()).isEqualTo(LocalDateTime.parse("2024-01-01T00:00"));
        assertThat(result.hourly().get(0).temperature()).isEqualTo(12.5);
        assertThat(result.hourly().get(0).description()).isEqualTo("Mainly clear");
        assertThat(result.hourly().get(0).precipitationProbability()).isEqualTo(10);
        assertThat(result.daily()).hasSize(2);
        assertThat(result.daily().get(1).date()).isEqualTo(LocalDate.parse("2024-01-02"));
        assertThat(result.daily().get(1).temperatureMax()).isEqualTo(16.2);
        assertThat(result.daily().get(1).temperatureMin()).isEqualTo(9.0);
        assertThat(result.daily().get(1).description()).isEqualTo("Overcast");
        assertThat(result.daily().get(1).sunrise()).isEqualTo(LocalDateTime.parse("2024-01-02T07:45"));
        assertThat(result.daily().get(1).sunset()).isEqualTo(LocalDateTime.parse("2024-01-02T17:31"));
        assertThat(result.daily().get(1).uvIndexMax()).isEqualTo(3.8);
        assertThat(result.daily().get(1).precipitationProbabilityMax()).isEqualTo(30);
    }

    @Test
    void returnsZeroDefaults_whenDailyOrHourlyValuesAreNull() {
        // Open-Meteo's model doesn't reliably cover its full range for every
        // derived metric — uv_index_max (and similar) can come back null for
        // the last day(s) of a 16-day forecast even though core fields stay
        // populated. Confirmed live against Madrid: index 15's uv_index_max
        // was null while every other field had a value.
        stubGeocoding("Lisboa", """
                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333, "population": 517802}]}
                """);
        stubForecastSeries("""
                {
                  "hourly": {
                    "time": ["2024-01-01T00:00"],
                    "temperature_2m": [12.5],
                    "weather_code": [1],
                    "precipitation_probability": [null]
                  },
                  "daily": {
                    "time": ["2024-01-01"],
                    "temperature_2m_max": [15.0],
                    "temperature_2m_min": [8.1],
                    "weather_code": [1],
                    "sunrise": ["2024-01-01T07:45"],
                    "sunset": ["2024-01-01T17:30"],
                    "uv_index_max": [null],
                    "precipitation_probability_max": [null]
                  }
                }
                """);

        ForecastData result = provider.fetchForecast("Lisboa", Units.METRIC);

        assertThat(result.hourly().get(0).precipitationProbability()).isZero();
        assertThat(result.daily().get(0).uvIndexMax()).isZero();
        assertThat(result.daily().get(0).precipitationProbabilityMax()).isZero();
    }

    @Test
    void dropsTrailingDayOrHour_whenCoreFieldsAreEntirelyNull() {
        // Confirmed live: for some locations Open-Meteo's model doesn't reach the
        // full requested range — the last day/hour comes back with every field
        // null (not a shorter array). A day/hour missing its core temperature or
        // weather code isn't real data — it must be dropped, not faked as 0°C.
        stubGeocoding("Lisboa", """
                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333, "population": 517802}]}
                """);
        stubForecastSeries("""
                {
                  "hourly": {
                    "time": ["2024-01-01T00:00", "2024-01-01T01:00"],
                    "temperature_2m": [12.5, null],
                    "weather_code": [1, null],
                    "precipitation_probability": [10, null]
                  },
                  "daily": {
                    "time": ["2024-01-01", "2024-01-02"],
                    "temperature_2m_max": [15.0, null],
                    "temperature_2m_min": [8.1, null],
                    "weather_code": [1, null],
                    "sunrise": ["2024-01-01T07:45", "2024-01-02T07:45"],
                    "sunset": ["2024-01-01T17:30", "2024-01-02T17:31"],
                    "uv_index_max": [3.5, null],
                    "precipitation_probability_max": [20, null]
                  }
                }
                """);

        ForecastData result = provider.fetchForecast("Lisboa", Units.METRIC);

        assertThat(result.hourly()).hasSize(1);
        assertThat(result.daily()).hasSize(1);
        assertThat(result.daily().get(0).date()).isEqualTo(LocalDate.parse("2024-01-01"));
    }

    @Test
    void forecastThrowsCityNotFound_whenGeocodingReturnsNoResults() {
        stubGeocoding("Atlantis", """
                {"results": []}
                """);

        assertThatThrownBy(() -> provider.fetchForecast("Atlantis", Units.METRIC))
                .isInstanceOf(CityNotFoundException.class);
    }

    @Test
    void forecastThrowsProviderUnavailable_whenForecastSeriesReturnsServerError() {
        stubGeocoding("Lisboa", """
                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333, "population": 517802}]}
                """);
        wireMock.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> provider.fetchForecast("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void searchCitiesReturnsAllMatches_whenProviderRespondsSuccessfully() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .withQueryParam("name", equalTo("Lis"))
                .withQueryParam("count", equalTo("5"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"results": [
                                    {"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333},
                                    {"name": "Lissa", "country": "Poland", "latitude": 51.2, "longitude": 15.6}
                                ]}
                                """)));

        List<GeocodingResult> results = provider.searchCities("Lis", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).name()).isEqualTo("Lisbon");
        assertThat(results.get(1).name()).isEqualTo("Lissa");
    }

    @Test
    void searchCitiesReturnsEmptyList_whenNoMatches() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .withQueryParam("name", equalTo("Atlantis"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"results": []}
                                """)));

        List<GeocodingResult> results = provider.searchCities("Atlantis", 5);

        assertThat(results).isEmpty();
    }

    @Test
    void searchCitiesThrowsProviderUnavailable_whenProviderReturnsServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> provider.searchCities("Lisboa", 5))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void returnsMarineConditions_whenCityIsCoastal() {
        stubGeocoding("Lisboa", """
                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333, "population": 517802}]}
                """);
        stubMarine("""
                {
                  "hourly": {
                    "time": ["2024-01-01T00:00", "2024-01-01T01:00"],
                    "wave_height": [1.2, 1.3],
                    "wave_direction": [270.0, 272.0],
                    "wave_period": [6.5, 6.7],
                    "sea_surface_temperature": [16.8, 16.7],
                    "sea_level_height_msl": [0.1, 0.2]
                  }
                }
                """);

        MarineData result = provider.fetchMarineConditions("Lisboa", Units.METRIC);

        assertThat(result.city()).isEqualTo("Lisbon");
        assertThat(result.country()).isEqualTo("Portugal");
        assertThat(result.provider()).isEqualTo("open-meteo");
        assertThat(result.waterTemperature()).isEqualTo(16.8);
        assertThat(result.waveHeightMeters()).isEqualTo(1.2);
        assertThat(result.waveDirectionDegrees()).isEqualTo(270.0);
        assertThat(result.wavePeriodSeconds()).isEqualTo(6.5);
    }

    @Test
    void returnsTideEvents_derivedFromSeaLevelHeightSeries() {
        stubGeocoding("Cascais", """
                {"results": [{"name": "Cascais", "country": "Portugal", "latitude": 38.6979, "longitude": -9.4215, "population": 206479}]}
                """);
        stubMarine("""
                {
                  "hourly": {
                    "time": ["2024-01-01T00:00", "2024-01-01T01:00", "2024-01-01T02:00", "2024-01-01T03:00", "2024-01-01T04:00"],
                    "wave_height": [1.2, 1.2, 1.2, 1.2, 1.2],
                    "wave_direction": [270.0, 270.0, 270.0, 270.0, 270.0],
                    "wave_period": [6.5, 6.5, 6.5, 6.5, 6.5],
                    "sea_surface_temperature": [16.8, 16.8, 16.8, 16.8, 16.8],
                    "sea_level_height_msl": [0.2, 0.9, 0.3, -0.6, 0.1]
                  }
                }
                """);

        MarineData result = provider.fetchMarineConditions("Cascais", Units.METRIC);

        assertThat(result.tideEvents()).containsExactly(
                new TideEvent(TideEvent.HIGH, "2024-01-01T01:00"),
                new TideEvent(TideEvent.LOW, "2024-01-01T03:00")
        );
    }

    @Test
    void returnsNullMarineFields_whenCityIsInland() {
        stubGeocoding("Madrid", """
                {"results": [{"name": "Madrid", "country": "Spain", "latitude": 40.4168, "longitude": -3.7038, "population": 3223000}]}
                """);
        stubMarine("""
                {
                  "hourly": {
                    "time": ["2024-01-01T00:00", "2024-01-01T01:00"],
                    "wave_height": [null, null],
                    "wave_direction": [null, null],
                    "wave_period": [null, null],
                    "sea_surface_temperature": [null, null]
                  }
                }
                """);

        MarineData result = provider.fetchMarineConditions("Madrid", Units.METRIC);

        assertThat(result.city()).isEqualTo("Madrid");
        assertThat(result.waterTemperature()).isNull();
        assertThat(result.waveHeightMeters()).isNull();
        assertThat(result.waveDirectionDegrees()).isNull();
        assertThat(result.wavePeriodSeconds()).isNull();
    }

    @Test
    void marineConditionsThrowsCityNotFound_whenGeocodingReturnsNoResults() {
        stubGeocoding("Atlantis", """
                {"results": []}
                """);

        assertThatThrownBy(() -> provider.fetchMarineConditions("Atlantis", Units.METRIC))
                .isInstanceOf(CityNotFoundException.class);
    }

    @Test
    void marineConditionsThrowsProviderUnavailable_whenMarineApiReturnsServerError() {
        stubGeocoding("Lisboa", """
                {"results": [{"name": "Lisbon", "country": "Portugal", "latitude": 38.7167, "longitude": -9.1333, "population": 517802}]}
                """);
        wireMock.stubFor(get(urlPathEqualTo("/v1/marine"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> provider.fetchMarineConditions("Lisboa", Units.METRIC))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    private void stubMarine(String responseBody) {
        wireMock.stubFor(get(urlPathEqualTo("/v1/marine"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    private void stubForecastSeries(String responseBody) {
        wireMock.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
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
