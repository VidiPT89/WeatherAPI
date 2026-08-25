package com.vidi.weather.provider;

import com.vidi.weather.config.WeatherApiProperties;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.DailyForecast;
import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.HourlyForecast;
import com.vidi.weather.model.MarineData;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.provider.openmeteo.ForecastResponse;
import com.vidi.weather.provider.openmeteo.GeocodingResponse;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import com.vidi.weather.provider.openmeteo.MarineResponse;
import com.vidi.weather.util.FishingConditionScorer;
import com.vidi.weather.util.OutdoorActivityScorer;
import com.vidi.weather.util.SurfConditionScorer;
import com.vidi.weather.util.TidePeakDetector;
import com.vidi.weather.util.UvRiskLabeler;
import com.vidi.weather.util.WeatherCodeMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Order(1)
public class OpenMeteoProvider implements WeatherProvider {

    private static final String PROVIDER_NAME = "open-meteo";
    private static final int FORECAST_HOURLY_HOURS = 48;
    private static final int FORECAST_DAILY_DAYS = 16;
    // Simple threshold for the "rain likely" flag surfaced per forecast day.
    private static final int RAIN_LIKELY_THRESHOLD_PERCENT = 50;
    // Open-Meteo indexes cities under their English name, so a local-spelling query needs this fallback locale to match (app is PT/EN).
    private static final String DISAMBIGUATION_LANGUAGE = "pt";

    private final RestTemplate restTemplate;
    private final WeatherApiProperties properties;

    public OpenMeteoProvider(RestTemplate weatherRestTemplate, WeatherApiProperties properties) {
        this.restTemplate = weatherRestTemplate;
        this.properties = properties;
    }

    @Override
    public WeatherData fetchCurrentWeather(String city, Units units) {
        GeocodingResult location = resolveLocation(city);
        ForecastResponse.CurrentWeather current = fetchCurrentConditions(location, units);

        return new WeatherData(
                location.name(),
                location.country(),
                current.temperature(),
                current.feelsLike(),
                current.humidity(),
                current.windSpeed(),
                WeatherCodeMapper.describe(current.weatherCode()),
                units,
                PROVIDER_NAME,
                Instant.now()
        );
    }

    public ForecastData fetchForecast(String city, Units units) {
        GeocodingResult location = resolveLocation(city);
        ForecastResponse response = fetchForecastSeries(location, units);
        // Best-effort: marine enrichment (wave/surf/fishing per day) must never break the
        // core forecast for an inland city or a flaky marine endpoint.
        MarineResponse marineResponse = fetchMarineSeriesSafely(location, units);

        // Open-Meteo's model doesn't always reach the full requested range for a
        // given location — the last hour(s)/day(s) can come back with every field
        // null instead of a shorter array. Confirmed live for Madrid: day 16's
        // temperatureMax/temperatureMin/weatherCode/uvIndexMax were all null.
        // Drop those incomplete trailing entries instead of faking zeros/defaults.
        ForecastResponse.Hourly hourlyResponse = response.hourly();
        List<HourlyForecast> hourly = IntStream.range(0, hourlyResponse.time().size())
                .filter(i -> hourlyResponse.temperature2m().get(i) != null && hourlyResponse.weatherCode().get(i) != null)
                .mapToObj(i -> new HourlyForecast(
                        LocalDateTime.parse(hourlyResponse.time().get(i)),
                        hourlyResponse.temperature2m().get(i),
                        WeatherCodeMapper.describe(hourlyResponse.weatherCode().get(i)),
                        intOrZero(hourlyResponse.precipitationProbability(), i)))
                .toList();

        ForecastResponse.Daily dailyResponse = response.daily();
        List<DailyForecast> daily = IntStream.range(0, dailyResponse.time().size())
                .filter(i -> dailyResponse.temperatureMax().get(i) != null
                        && dailyResponse.temperatureMin().get(i) != null
                        && dailyResponse.weatherCode().get(i) != null)
                .mapToObj(i -> buildDailyForecast(dailyResponse, marineResponse, i, units))
                .toList();

        return new ForecastData(location.name(), location.country(), units, PROVIDER_NAME, hourly, daily);
    }

    private DailyForecast buildDailyForecast(ForecastResponse.Daily dailyResponse, MarineResponse marineResponse, int index, Units units) {
        double temperatureMax = dailyResponse.temperatureMax().get(index);
        double temperatureMin = dailyResponse.temperatureMin().get(index);
        double uvIndexMax = doubleOrZero(dailyResponse.uvIndexMax(), index);
        int precipitationProbabilityMax = intOrZero(dailyResponse.precipitationProbabilityMax(), index);
        double windSpeedMax = doubleOrZero(dailyResponse.windSpeedMax(), index);

        Double waveHeightMax = marineDailyValue(marineResponse, index, MarineResponse.Daily::waveHeightMax);
        Double wavePeriodMax = marineDailyValue(marineResponse, index, MarineResponse.Daily::wavePeriodMax);

        boolean rainLikely = precipitationProbabilityMax >= RAIN_LIKELY_THRESHOLD_PERCENT;
        String uvRiskLabel = UvRiskLabeler.label(uvIndexMax);
        int activityScore = OutdoorActivityScorer.score(
                temperatureMax, windSpeedMax, precipitationProbabilityMax, uvIndexMax, units);
        String outdoorActivityLabel = OutdoorActivityScorer.label(activityScore);
        String fishingConditionLabel = FishingConditionScorer.label(waveHeightMax, windSpeedMax, units);
        String surfConditionLabel = SurfConditionScorer.label(waveHeightMax, wavePeriodMax);

        return new DailyForecast(
                LocalDate.parse(dailyResponse.time().get(index)),
                temperatureMax,
                temperatureMin,
                WeatherCodeMapper.describe(dailyResponse.weatherCode().get(index)),
                LocalDateTime.parse(dailyResponse.sunrise().get(index)),
                LocalDateTime.parse(dailyResponse.sunset().get(index)),
                uvIndexMax,
                precipitationProbabilityMax,
                windSpeedMax,
                waveHeightMax,
                wavePeriodMax,
                rainLikely,
                uvRiskLabel,
                outdoorActivityLabel,
                fishingConditionLabel,
                surfConditionLabel);
    }

    private MarineResponse fetchMarineSeriesSafely(GeocodingResult location, Units units) {
        try {
            return fetchMarineSeries(location, units);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Double marineDailyValue(
            MarineResponse marineResponse, int index, Function<MarineResponse.Daily, List<Double>> extractor) {
        if (marineResponse == null || marineResponse.daily() == null) {
            return null;
        }
        List<Double> values = extractor.apply(marineResponse.daily());
        return (values == null || index >= values.size()) ? null : values.get(index);
    }

    public MarineData fetchMarineConditions(String city, Units units) {
        GeocodingResult location = resolveLocation(city);
        MarineResponse response = fetchMarineSeries(location, units);

        MarineResponse.Hourly hourly = response.hourly();
        boolean hasReadings = hourly != null && !hourly.time().isEmpty();

        return new MarineData(
                location.name(),
                location.country(),
                units,
                PROVIDER_NAME,
                hasReadings ? firstReading(hourly.seaSurfaceTemperature()) : null,
                hasReadings ? firstReading(hourly.waveHeight()) : null,
                hasReadings ? firstReading(hourly.waveDirection()) : null,
                hasReadings ? firstReading(hourly.wavePeriod()) : null,
                hasReadings ? TidePeakDetector.detect(hourly.time(), hourly.seaLevelHeightMsl()) : List.of()
        );
    }

    /**
     * Open-Meteo can omit an individual hourly series entirely (the field comes back `null`,
     * not just an empty/null-filled list) for a location it has partial marine coverage for,
     * even while {@code hourly.time()} itself is populated -- calling {@code .get(0)} directly
     * on such a list would throw an NPE instead of yielding the "no data" `null` these fields
     * are supposed to represent.
     */
    private static Double firstReading(List<Double> series) {
        return (series == null || series.isEmpty()) ? null : series.get(0);
    }

    public List<GeocodingResult> searchCities(String query, int limit) {
        return fetchGeocodingResults(query, limit, null);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    // Lets a caller disambiguate same-named cities (e.g. Beja, Portugal vs. Beja, Tunisia) by
    // appending ", <country>" -- the exact format the search-suggestion dropdown in every client
    // now submits, since Open-Meteo's own relevance ranking for a bare name isn't population-only
    // and can rank a foreign namesake above the intended city.
    private GeocodingResult resolveLocation(String city) {
        String cityName = city;
        String countryHint = null;
        int lastComma = city.lastIndexOf(',');
        if (lastComma > 0 && lastComma < city.length() - 1) {
            cityName = city.substring(0, lastComma).trim();
            countryHint = city.substring(lastComma + 1).trim();
        }

        if (countryHint != null) {
            GeocodingResult matched = resolveWithCountryHint(cityName, countryHint);
            if (matched != null) {
                return matched;
            }
        }

        List<GeocodingResult> primary = fetchGeocodingResults(cityName, 1, null);
        if (isConfidentMatch(primary)) {
            return primary.get(0);
        }

        List<GeocodingResult> localized = fetchGeocodingResults(cityName, 1, DISAMBIGUATION_LANGUAGE);
        if (isConfidentMatch(localized)) {
            return localized.get(0);
        }

        List<GeocodingResult> fallback = !primary.isEmpty() ? primary : localized;
        if (fallback.isEmpty()) {
            throw new CityNotFoundException(city);
        }
        return fallback.get(0);
    }

    private static final int COUNTRY_HINT_CANDIDATE_COUNT = 10;

    private GeocodingResult resolveWithCountryHint(String cityName, String countryHint) {
        List<GeocodingResult> candidates = fetchGeocodingResults(cityName, COUNTRY_HINT_CANDIDATE_COUNT, null);
        GeocodingResult match = firstMatchingCountry(candidates, countryHint);
        if (match != null) {
            return match;
        }

        List<GeocodingResult> localizedCandidates =
                fetchGeocodingResults(cityName, COUNTRY_HINT_CANDIDATE_COUNT, DISAMBIGUATION_LANGUAGE);
        return firstMatchingCountry(localizedCandidates, countryHint);
    }

    private static GeocodingResult firstMatchingCountry(List<GeocodingResult> candidates, String countryHint) {
        return candidates.stream()
                .filter(result -> result.country() != null && result.country().equalsIgnoreCase(countryHint))
                .findFirst()
                .orElse(null);
    }

    // Notable places carry population data; unrelated place-name collisions (e.g. Mozambican villages named "Lisboa") don't.
    private boolean isConfidentMatch(List<GeocodingResult> results) {
        return !results.isEmpty() && results.get(0).population() != null;
    }

    private List<GeocodingResult> fetchGeocodingResults(String query, int count, String language) {
        GeocodingResponse response = fetchGeocodingResponse(query, count, language);
        return response == null || response.results() == null ? List.of() : response.results();
    }

    /** Appends the API key when configured -- see {@link WeatherApiProperties.OpenMeteo}. */
    private UriComponentsBuilder openMeteoUri(String baseUrl) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
        String apiKey = properties.openMeteo().apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("apikey", apiKey);
        }
        return builder;
    }

    private GeocodingResponse fetchGeocodingResponse(String query, int count, String language) {
        UriComponentsBuilder builder = openMeteoUri(properties.openMeteo().geocodingUrl())
                .queryParam("name", query)
                .queryParam("count", count)
                .queryParam("format", "json");
        if (language != null) {
            builder.queryParam("language", language);
        }
        String uri = builder.toUriString();

        return execute(() -> restTemplate.getForObject(uri, GeocodingResponse.class));
    }

    private ForecastResponse.CurrentWeather fetchCurrentConditions(GeocodingResult location, Units units) {
        String temperatureUnit = units == Units.IMPERIAL ? "fahrenheit" : "celsius";
        String windSpeedUnit = units == Units.IMPERIAL ? "mph" : "kmh";

        String uri = openMeteoUri(properties.openMeteo().forecastUrl())
                .queryParam("latitude", location.latitude())
                .queryParam("longitude", location.longitude())
                .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code")
                .queryParam("temperature_unit", temperatureUnit)
                .queryParam("wind_speed_unit", windSpeedUnit)
                .toUriString();

        ForecastResponse response = execute(() -> restTemplate.getForObject(uri, ForecastResponse.class));

        if (response == null || response.current() == null) {
            throw new ProviderUnavailableException(PROVIDER_NAME, null);
        }
        return response.current();
    }

    private ForecastResponse fetchForecastSeries(GeocodingResult location, Units units) {
        String temperatureUnit = units == Units.IMPERIAL ? "fahrenheit" : "celsius";
        String windSpeedUnit = units == Units.IMPERIAL ? "mph" : "kmh";

        String uri = openMeteoUri(properties.openMeteo().forecastUrl())
                .queryParam("latitude", location.latitude())
                .queryParam("longitude", location.longitude())
                .queryParam("hourly", "temperature_2m,weather_code,precipitation_probability")
                .queryParam("daily",
                        "temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset,"
                                + "uv_index_max,precipitation_probability_max,wind_speed_10m_max")
                .queryParam("temperature_unit", temperatureUnit)
                .queryParam("wind_speed_unit", windSpeedUnit)
                .queryParam("timezone", "auto")
                .queryParam("forecast_hours", FORECAST_HOURLY_HOURS)
                .queryParam("forecast_days", FORECAST_DAILY_DAYS)
                .toUriString();

        ForecastResponse response = execute(() -> restTemplate.getForObject(uri, ForecastResponse.class));

        if (response == null || response.hourly() == null || response.daily() == null) {
            throw new ProviderUnavailableException(PROVIDER_NAME, null);
        }
        return response;
    }

    private static double doubleOrZero(List<Double> values, int index) {
        if (values == null || index >= values.size()) {
            return 0.0;
        }
        Double value = values.get(index);
        return value != null ? value : 0.0;
    }

    private static int intOrZero(List<Integer> values, int index) {
        if (values == null || index >= values.size()) {
            return 0;
        }
        Integer value = values.get(index);
        return value != null ? value : 0;
    }

    /**
     * Shared by the "today" marine card ({@link #fetchMarineConditions}) and the per-day
     * fishing/surf labels in {@link #fetchForecast} -- one request carries both the hourly
     * block (today's readings) and the daily block (max wave height/period per day), so
     * enriching the forecast doesn't cost an extra Open-Meteo call.
     */
    private MarineResponse fetchMarineSeries(GeocodingResult location, Units units) {
        String temperatureUnit = units == Units.IMPERIAL ? "fahrenheit" : "celsius";

        String uri = openMeteoUri(properties.openMeteo().marineUrl())
                .queryParam("latitude", location.latitude())
                .queryParam("longitude", location.longitude())
                .queryParam("hourly", "wave_height,wave_direction,wave_period,sea_surface_temperature,sea_level_height_msl")
                .queryParam("daily", "wave_height_max,wave_period_max")
                .queryParam("temperature_unit", temperatureUnit)
                .queryParam("timezone", "auto")
                .queryParam("forecast_days", FORECAST_DAILY_DAYS)
                .toUriString();

        MarineResponse response = execute(() -> restTemplate.getForObject(uri, MarineResponse.class));

        if (response == null) {
            throw new ProviderUnavailableException(PROVIDER_NAME, null);
        }
        return response;
    }

    private <T> T execute(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderQuotaExceededException(PROVIDER_NAME);
        } catch (RestClientException ex) {
            throw new ProviderUnavailableException(PROVIDER_NAME, ex);
        }
    }
}
