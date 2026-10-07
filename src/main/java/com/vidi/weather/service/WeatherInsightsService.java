package com.vidi.weather.service;

import com.vidi.weather.model.DailyForecast;
import com.vidi.weather.model.MoonPhaseInfo;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherInsightsData;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import com.vidi.weather.util.FishingConditionScorer;
import com.vidi.weather.util.MoonPhaseCalculator;
import com.vidi.weather.util.OutdoorActivityScorer;
import com.vidi.weather.util.UvRiskLabeler;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Aggregates derived, "nice to have" insights (moon phase, UV risk, outdoor-activity score,
 * fishing conditions) from data the app already fetches — current weather, forecast and marine
 * conditions — rather than owning any provider call of its own. All three underlying services
 * are independently cached, so this adds no new Open-Meteo traffic beyond what the Dashboard
 * already triggers.
 */
@Service
public class WeatherInsightsService {

    private final WeatherAggregatorService weatherAggregatorService;
    private final ForecastService forecastService;
    private final MarineService marineService;

    public WeatherInsightsService(
            WeatherAggregatorService weatherAggregatorService,
            ForecastService forecastService,
            MarineService marineService) {
        this.weatherAggregatorService = weatherAggregatorService;
        this.forecastService = forecastService;
        this.marineService = marineService;
    }

    public WeatherInsightsData getInsights(String city, Units units) {
        return buildInsights(
                weatherAggregatorService.getCurrentWeather(city, units).data(),
                forecastService.getForecast(city, units).data().daily(),
                marineService.getMarineConditions(city, units).data().waveHeightMeters(),
                units);
    }

    /** Insights for exact coordinates; see {@link ForecastService#getForecastAt}. */
    public WeatherInsightsData getInsightsAt(GeocodingResult location, Units units) {
        return buildInsights(
                weatherAggregatorService.getCurrentWeatherByCoordinates(
                        location.latitude(), location.longitude(), location.name(), units).data(),
                forecastService.getForecastAt(location, units).data().daily(),
                marineService.getMarineConditionsAt(location, units).data().waveHeightMeters(),
                units);
    }

    private WeatherInsightsData buildInsights(
            WeatherData weather, List<DailyForecast> daily, Double waveHeightMeters, Units units) {
        DailyForecast today = daily.isEmpty() ? null : daily.get(0);

        double uvIndexMax = today != null ? today.uvIndexMax() : 0;
        int precipitationProbabilityMax = today != null ? today.precipitationProbabilityMax() : 0;

        MoonPhaseInfo moonPhase = MoonPhaseCalculator.calculate(LocalDate.now());
        String uvRiskLabel = UvRiskLabeler.label(uvIndexMax);
        int activityScore = OutdoorActivityScorer.score(
                weather.temperature(), weather.windSpeed(), precipitationProbabilityMax, uvIndexMax, units);
        String activityLabel = OutdoorActivityScorer.label(activityScore);
        String fishingConditionLabel = FishingConditionScorer.label(waveHeightMeters, weather.windSpeed(), units);

        return new WeatherInsightsData(
                weather.city(), weather.country(), moonPhase, uvRiskLabel, activityScore, activityLabel, fishingConditionLabel);
    }
}
