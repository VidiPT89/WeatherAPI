package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.vidi.weather.model.DailyForecast;
import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.ForecastResult;
import com.vidi.weather.model.MarineData;
import com.vidi.weather.model.MarineResult;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherInsightsData;
import com.vidi.weather.model.WeatherResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherInsightsServiceTest {

    @Mock
    private WeatherAggregatorService weatherAggregatorService;

    @Mock
    private ForecastService forecastService;

    @Mock
    private MarineService marineService;

    private WeatherInsightsService weatherInsightsService;

    @BeforeEach
    void setUp() {
        weatherInsightsService = new WeatherInsightsService(weatherAggregatorService, forecastService, marineService);
    }

    @Test
    void combinesWeatherForecastAndMarineData_intoDerivedInsights() {
        WeatherData weather = new WeatherData(
                "Cascais", "Portugal", 21.0, 20.5, 60, 10.0, "Clear sky", Units.METRIC, "open-meteo", Instant.now());
        DailyForecast today = new DailyForecast(
                LocalDate.now(), 24.0, 16.0, "Clear sky", LocalDateTime.now(), LocalDateTime.now(), 4.0, 10);
        ForecastData forecast = new ForecastData("Cascais", "Portugal", Units.METRIC, "open-meteo", List.of(), List.of(today));
        MarineData marine = new MarineData("Cascais", "Portugal", Units.METRIC, "open-meteo", 17.0, 0.4, 270.0, 6.0, List.of());

        when(weatherAggregatorService.getCurrentWeather(eq("Cascais"), eq(Units.METRIC)))
                .thenReturn(new WeatherResult(weather, false));
        when(forecastService.getForecast(eq("Cascais"), eq(Units.METRIC)))
                .thenReturn(new ForecastResult(forecast, false));
        when(marineService.getMarineConditions(eq("Cascais"), eq(Units.METRIC)))
                .thenReturn(new MarineResult(marine, false));

        WeatherInsightsData insights = weatherInsightsService.getInsights("Cascais", Units.METRIC);

        assertThat(insights.city()).isEqualTo("Cascais");
        assertThat(insights.uvRiskLabel()).isEqualTo("Moderate");
        assertThat(insights.fishingConditionLabel()).isEqualTo("Good");
        assertThat(insights.outdoorActivityScore()).isBetween(0, 100);
        assertThat(insights.outdoorActivityLabel()).isNotBlank();
        assertThat(insights.moonPhase()).isNotNull();
    }

    @Test
    void fishingConditionIsNull_whenCityHasNoMarineData() {
        WeatherData weather = new WeatherData(
                "Madrid", "Spain", 18.0, 17.0, 50, 8.0, "Clear sky", Units.METRIC, "open-meteo", Instant.now());
        DailyForecast today = new DailyForecast(
                LocalDate.now(), 22.0, 12.0, "Clear sky", LocalDateTime.now(), LocalDateTime.now(), 2.0, 5);
        ForecastData forecast = new ForecastData("Madrid", "Spain", Units.METRIC, "open-meteo", List.of(), List.of(today));
        MarineData marine = new MarineData("Madrid", "Spain", Units.METRIC, "open-meteo", null, null, null, null, List.of());

        when(weatherAggregatorService.getCurrentWeather(eq("Madrid"), eq(Units.METRIC)))
                .thenReturn(new WeatherResult(weather, false));
        when(forecastService.getForecast(eq("Madrid"), eq(Units.METRIC)))
                .thenReturn(new ForecastResult(forecast, false));
        when(marineService.getMarineConditions(eq("Madrid"), eq(Units.METRIC)))
                .thenReturn(new MarineResult(marine, false));

        WeatherInsightsData insights = weatherInsightsService.getInsights("Madrid", Units.METRIC);

        assertThat(insights.fishingConditionLabel()).isNull();
        assertThat(insights.moonPhase()).isNotNull();
        assertThat(insights.uvRiskLabel()).isEqualTo("Low");
    }
}
