package com.vidi.weather.provider;

import com.vidi.weather.config.WeatherApiProperties;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import com.vidi.weather.provider.openweathermap.OpenWeatherMapReverseGeocodingEntry;
import com.vidi.weather.provider.openweathermap.OpenWeatherMapResponse;
import com.vidi.weather.util.UnitConverter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

// Primary: unlike Open-Meteo's key-less free tier, our API key here gets a quota dedicated to
// this app, not one shared with every other app on Render's free-tier egress IP -- see
// conhecimento/decisoes/ADR-001-hosting-free-projetos-pessoais.md.
@Component
@Order(1)
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

    /**
     * Resolves GPS coordinates to a city name via OpenWeatherMap's reverse-geocoding API --
     * Open-Meteo's free geocoding endpoint only supports forward (name -> coordinates) search.
     */
    public List<GeocodingResult> reverseGeocode(double latitude, double longitude) {
        String uri = UriComponentsBuilder.fromHttpUrl(properties.openWeatherMap().reverseGeocodingUrl())
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("limit", 1)
                .queryParam("appid", properties.openWeatherMap().apiKey())
                .toUriString();

        OpenWeatherMapReverseGeocodingEntry[] response;
        try {
            response = restTemplate.getForObject(uri, OpenWeatherMapReverseGeocodingEntry[].class);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderQuotaExceededException(PROVIDER_NAME);
        } catch (RestClientException ex) {
            throw new ProviderUnavailableException(PROVIDER_NAME, ex);
        }

        if (response == null) {
            return List.of();
        }

        return Arrays.stream(response)
                .map(entry -> new GeocodingResult(entry.name(), entry.country(), entry.lat(), entry.lon()))
                .toList();
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
