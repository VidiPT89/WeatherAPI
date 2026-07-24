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
import com.vidi.weather.util.TidePeakDetector;
import com.vidi.weather.util.WeatherCodeMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
                .mapToObj(i -> new DailyForecast(
                        LocalDate.parse(dailyResponse.time().get(i)),
                        dailyResponse.temperatureMax().get(i),
                        dailyResponse.temperatureMin().get(i),
                        WeatherCodeMapper.describe(dailyResponse.weatherCode().get(i)),
                        LocalDateTime.parse(dailyResponse.sunrise().get(i)),
                        LocalDateTime.parse(dailyResponse.sunset().get(i)),
                        doubleOrZero(dailyResponse.uvIndexMax(), i),
                        intOrZero(dailyResponse.precipitationProbabilityMax(), i)))
                .toList();

        return new ForecastData(location.name(), location.country(), units, PROVIDER_NAME, hourly, daily);
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
                hasReadings ? hourly.seaSurfaceTemperature().get(0) : null,
                hasReadings ? hourly.waveHeight().get(0) : null,
                hasReadings ? hourly.waveDirection().get(0) : null,
                hasReadings ? hourly.wavePeriod().get(0) : null,
                hasReadings ? TidePeakDetector.detect(hourly.time(), hourly.seaLevelHeightMsl()) : List.of()
        );
    }

    public List<GeocodingResult> searchCities(String query, int limit) {
        return fetchGeocodingResults(query, limit, null);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private GeocodingResult resolveLocation(String city) {
        List<GeocodingResult> primary = fetchGeocodingResults(city, 1, null);
        if (isConfidentMatch(primary)) {
            return primary.get(0);
        }

        List<GeocodingResult> localized = fetchGeocodingResults(city, 1, DISAMBIGUATION_LANGUAGE);
        if (isConfidentMatch(localized)) {
            return localized.get(0);
        }

        List<GeocodingResult> fallback = !primary.isEmpty() ? primary : localized;
        if (fallback.isEmpty()) {
            throw new CityNotFoundException(city);
        }
        return fallback.get(0);
    }

    // Notable places carry population data; unrelated place-name collisions (e.g. Mozambican villages named "Lisboa") don't.
    private boolean isConfidentMatch(List<GeocodingResult> results) {
        return !results.isEmpty() && results.get(0).population() != null;
    }

    private List<GeocodingResult> fetchGeocodingResults(String query, int count, String language) {
        GeocodingResponse response = fetchGeocodingResponse(query, count, language);
        return response == null || response.results() == null ? List.of() : response.results();
    }

    private GeocodingResponse fetchGeocodingResponse(String query, int count, String language) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.openMeteo().geocodingUrl())
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

        String uri = UriComponentsBuilder.fromHttpUrl(properties.openMeteo().forecastUrl())
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

        String uri = UriComponentsBuilder.fromHttpUrl(properties.openMeteo().forecastUrl())
                .queryParam("latitude", location.latitude())
                .queryParam("longitude", location.longitude())
                .queryParam("hourly", "temperature_2m,weather_code,precipitation_probability")
                .queryParam("daily",
                        "temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset,"
                                + "uv_index_max,precipitation_probability_max")
                .queryParam("temperature_unit", temperatureUnit)
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
        Double value = values.get(index);
        return value != null ? value : 0.0;
    }

    private static int intOrZero(List<Integer> values, int index) {
        Integer value = values.get(index);
        return value != null ? value : 0;
    }

    private MarineResponse fetchMarineSeries(GeocodingResult location, Units units) {
        String temperatureUnit = units == Units.IMPERIAL ? "fahrenheit" : "celsius";

        String uri = UriComponentsBuilder.fromHttpUrl(properties.openMeteo().marineUrl())
                .queryParam("latitude", location.latitude())
                .queryParam("longitude", location.longitude())
                .queryParam("hourly", "wave_height,wave_direction,wave_period,sea_surface_temperature,sea_level_height_msl")
                .queryParam("temperature_unit", temperatureUnit)
                .queryParam("timezone", "auto")
                .queryParam("forecast_days", 1)
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
