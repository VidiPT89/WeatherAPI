package com.vidi.weather.provider;

import com.vidi.weather.config.WeatherApiProperties;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.provider.openmeteo.ForecastResponse;
import com.vidi.weather.provider.openmeteo.GeocodingResponse;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import com.vidi.weather.util.WeatherCodeMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
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
        GeocodingResult location = geocode(city);
        ForecastResponse.CurrentWeather current = fetchForecast(location, units);

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

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private GeocodingResult geocode(String city) {
        String uri = UriComponentsBuilder.fromHttpUrl(properties.openMeteo().geocodingUrl())
                .queryParam("name", city)
                .queryParam("count", 1)
                .queryParam("format", "json")
                .toUriString();

        GeocodingResponse response = execute(() -> restTemplate.getForObject(uri, GeocodingResponse.class));

        List<GeocodingResult> results = response == null ? null : response.results();
        if (results == null || results.isEmpty()) {
            throw new CityNotFoundException(city);
        }
        return results.get(0);
    }

    private ForecastResponse.CurrentWeather fetchForecast(GeocodingResult location, Units units) {
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
