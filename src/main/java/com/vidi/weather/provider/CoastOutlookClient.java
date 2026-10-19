package com.vidi.weather.provider;

import com.vidi.weather.config.WeatherApiProperties;
import com.vidi.weather.exception.ProviderQuotaExceededException;
import com.vidi.weather.exception.ProviderUnavailableException;
import com.vidi.weather.provider.openmeteo.CoastMarineResponse;
import com.vidi.weather.provider.openmeteo.CoastWindResponse;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fetches the two Open-Meteo series the coast outlook needs for one point: the sea (marine API)
 * and the wind/air/daylight above it (forecast API). Always metric -- the scorers work in metric
 * and the response is converted for imperial callers afterwards. Both use {@code timezone=auto},
 * so their hourly timestamps are the same local hours and can be matched one to one.
 */
@Component
public class CoastOutlookClient {

    public static final String PROVIDER_NAME = "open-meteo";
    public static final int FORECAST_DAYS = 4;

    private final RestTemplate restTemplate;
    private final WeatherApiProperties properties;

    public CoastOutlookClient(RestTemplate weatherRestTemplate, WeatherApiProperties properties) {
        this.restTemplate = weatherRestTemplate;
        this.properties = properties;
    }

    public CoastMarineResponse fetchSea(double latitude, double longitude) {
        String uri = UriComponentsBuilder.fromHttpUrl(properties.openMeteo().marineUrl())
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "wave_height,wave_period,wave_direction,swell_wave_height,swell_wave_period,"
                        + "swell_wave_direction,sea_surface_temperature,sea_level_height_msl")
                .queryParam("timezone", "auto")
                .queryParam("forecast_days", FORECAST_DAYS)
                .toUriString();

        CoastMarineResponse response = execute(() -> restTemplate.getForObject(uri, CoastMarineResponse.class));
        if (response == null || response.hourly() == null || response.hourly().time() == null) {
            throw new ProviderUnavailableException(PROVIDER_NAME, null);
        }
        return response;
    }

    public CoastWindResponse fetchWind(double latitude, double longitude) {
        String uri = UriComponentsBuilder.fromHttpUrl(properties.openMeteo().forecastUrl())
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "wind_speed_10m,wind_direction_10m,wind_gusts_10m,temperature_2m,"
                        + "precipitation_probability")
                .queryParam("daily", "sunrise,sunset")
                .queryParam("timezone", "auto")
                .queryParam("forecast_days", FORECAST_DAYS)
                .toUriString();

        CoastWindResponse response = execute(() -> restTemplate.getForObject(uri, CoastWindResponse.class));
        if (response == null || response.hourly() == null || response.daily() == null) {
            throw new ProviderUnavailableException(PROVIDER_NAME, null);
        }
        return response;
    }

    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderQuotaExceededException(PROVIDER_NAME);
        } catch (RestClientException ex) {
            throw new ProviderUnavailableException(PROVIDER_NAME, ex);
        }
    }
}
