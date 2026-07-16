package com.vidi.weather.provider;

import com.vidi.weather.config.WeatherApiProperties;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.provider.openweathermap.OpenWeatherMapResponse;
import com.vidi.weather.util.UnitConverter;
import java.time.Instant;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Order(2)
public class OpenWeatherMapProvider implements WeatherProvider {

    private static final String PROVIDER_NAME = "open-weather-map";
    private static final String UNKNOWN_DESCRIPTION = "Unknown";

    private final RestTemplate restTemplate;
    private final WeatherApiProperties properties;

    public OpenWeatherMapProvider(RestTemplate weatherRestTemplate, WeatherApiProperties properties) {
        this.restTemplate = weatherRestTemplate;
        this.properties = properties;
    }

    @Override
    public WeatherData fetchCurrentWeather(String city, Units units) {
        String uri = UriComponentsBuilder.fromHttpUrl(properties.openWeatherMap().baseUrl())
                .queryParam("q", city)
                .queryParam("appid", properties.openWeatherMap().apiKey())
                .toUriString();

        OpenWeatherMapResponse response;
        try {
            response = restTemplate.getForObject(uri, OpenWeatherMapResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CityNotFoundException(city);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderQuotaExceededException(PROVIDER_NAME);
        } catch (RestClientException ex) {
            throw new ProviderUnavailableException(PROVIDER_NAME, ex);
        }

        if (response == null || response.main() == null) {
            throw new ProviderUnavailableException(PROVIDER_NAME, null);
        }

        return new WeatherData(
                response.name() != null ? response.name() : city,
                response.sys() != null ? response.sys().country() : null,
                UnitConverter.kelvinToRequestedTemperature(response.main().temp(), units),
                UnitConverter.kelvinToRequestedTemperature(response.main().feelsLike(), units),
                response.main().humidity(),
                response.wind() != null ? UnitConverter.metersPerSecondToRequestedSpeed(response.wind().speed(), units) : 0,
                describe(response.weather()),
                units,
                PROVIDER_NAME,
                Instant.now()
        );
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private String describe(List<OpenWeatherMapResponse.WeatherDescription> weather) {
        if (weather == null || weather.isEmpty()) {
            return UNKNOWN_DESCRIPTION;
        }
        return weather.get(0).description();
    }
}
