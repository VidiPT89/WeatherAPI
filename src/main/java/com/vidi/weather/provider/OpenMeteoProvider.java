package com.vidi.weather.provider;

import com.vidi.weather.config.WeatherApiProperties;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.DailyForecast;
import com.vidi.weather.model.ForecastData;
import com.vidi.weather.model.HourlyForecast;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.provider.openmeteo.ForecastResponse;
import com.vidi.weather.provider.openmeteo.GeocodingResponse;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
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

        List<HourlyForecast> hourly = IntStream.range(0, response.hourly().time().size())
                .mapToObj(i -> new HourlyForecast(
                        LocalDateTime.parse(response.hourly().time().get(i)),
                        response.hourly().temperature2m().get(i),
                        WeatherCodeMapper.describe(response.hourly().weatherCode().get(i))))
                .toList();

        List<DailyForecast> daily = IntStream.range(0, response.daily().time().size())
                .mapToObj(i -> new DailyForecast(
                        LocalDate.parse(response.daily().time().get(i)),
                        response.daily().temperatureMax().get(i),
                        response.daily().temperatureMin().get(i),
                        WeatherCodeMapper.describe(response.daily().weatherCode().get(i))))
                .toList();

        return new ForecastData(location.name(), location.country(), units, PROVIDER_NAME, hourly, daily);
    }

    public List<GeocodingResult> searchCities(String query, int limit) {
        GeocodingResponse response = fetchGeocodingResponse(query, limit);
        return response == null || response.results() == null ? List.of() : response.results();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private GeocodingResult resolveLocation(String city) {
        GeocodingResponse response = fetchGeocodingResponse(city, 1);
        List<GeocodingResult> results = response == null ? null : response.results();
        if (results == null || results.isEmpty()) {
            throw new CityNotFoundException(city);
        }
        return results.get(0);
    }

    private GeocodingResponse fetchGeocodingResponse(String query, int count) {
        String uri = UriComponentsBuilder.fromHttpUrl(properties.openMeteo().geocodingUrl())
                .queryParam("name", query)
                .queryParam("count", count)
                .queryParam("format", "json")
                .toUriString();

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
                .queryParam("hourly", "temperature_2m,weather_code")
                .queryParam("daily", "temperature_2m_max,temperature_2m_min,weather_code")
                .queryParam("temperature_unit", temperatureUnit)
                .queryParam("timezone", "auto")
                .queryParam("forecast_days", 3)
                .toUriString();

        ForecastResponse response = execute(() -> restTemplate.getForObject(uri, ForecastResponse.class));

        if (response == null || response.hourly() == null || response.daily() == null) {
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
