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
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import com.vidi.weather.provider.openweathermap.OpenWeatherMapForecastResponse;
import com.vidi.weather.provider.openweathermap.OpenWeatherMapReverseGeocodingEntry;
import com.vidi.weather.provider.openweathermap.OpenWeatherMapResponse;
import com.vidi.weather.util.OutdoorActivityScorer;
import com.vidi.weather.util.UnitConverter;
import com.vidi.weather.util.UvRiskLabeler;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        return fetchAndConvert(uri, city, units);
    }

    @Override
    public WeatherData fetchCurrentWeatherByCoordinates(double latitude, double longitude, String cityName, Units units) {
        String uri = UriComponentsBuilder.fromHttpUrl(properties.openWeatherMap().baseUrl())
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("appid", properties.openWeatherMap().apiKey())
                .toUriString();

        return fetchAndConvert(uri, cityName, units);
    }

    private WeatherData fetchAndConvert(String uri, String fallbackName, Units units) {
        OpenWeatherMapResponse response;
        try {
            response = restTemplate.getForObject(uri, OpenWeatherMapResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CityNotFoundException(fallbackName);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderQuotaExceededException(PROVIDER_NAME);
        } catch (RestClientException ex) {
            throw new ProviderUnavailableException(PROVIDER_NAME, ex);
        }

        if (response == null || response.main() == null) {
            throw new ProviderUnavailableException(PROVIDER_NAME, null);
        }

        return new WeatherData(
                response.name() != null ? response.name() : fallbackName,
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

    /**
     * Fallback forecast source for when Open-Meteo (the everyday primary -- richer data: hourly
     * UV/rain-probability, 16 days ahead, actual daily sunrise/sunset) is unavailable, e.g. its
     * shared-IP quota on Render (see ADR-001). OpenWeatherMap's free tier only gives 3-hour steps
     * over 5 days, no UV index, and one sunrise/sunset reused as an approximation for every day
     * (see {@link OpenWeatherMapForecastResponse.City}) -- degraded, but far better than no
     * forecast at all.
     */
    public ForecastData fetchForecast(String city, Units units) {
        String uri = UriComponentsBuilder.fromHttpUrl(properties.openWeatherMap().forecastUrl())
                .queryParam("q", city)
                .queryParam("appid", properties.openWeatherMap().apiKey())
                .toUriString();

        OpenWeatherMapForecastResponse response;
        try {
            response = restTemplate.getForObject(uri, OpenWeatherMapForecastResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CityNotFoundException(city);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderQuotaExceededException(PROVIDER_NAME);
        } catch (RestClientException ex) {
            throw new ProviderUnavailableException(PROVIDER_NAME, ex);
        }

        if (response == null || response.list() == null || response.list().isEmpty() || response.city() == null) {
            throw new ProviderUnavailableException(PROVIDER_NAME, null);
        }

        ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(response.city().timezone());
        LocalDateTime sunrise = toLocalDateTime(response.city().sunrise(), zoneOffset);
        LocalDateTime sunset = toLocalDateTime(response.city().sunset(), zoneOffset);

        List<HourlyForecast> hourly = response.list().stream()
                .limit(16) // 3h steps -- 16 entries covers 48h, matching Open-Meteo's hourly window.
                .map(entry -> new HourlyForecast(
                        toLocalDateTime(entry.dt(), zoneOffset),
                        UnitConverter.kelvinToRequestedTemperature(entry.main().temp(), units),
                        describe(entry.weather()),
                        (int) Math.round(entry.pop() * 100)))
                .toList();

        Map<LocalDate, List<OpenWeatherMapForecastResponse.Entry>> byDay = new LinkedHashMap<>();
        for (OpenWeatherMapForecastResponse.Entry entry : response.list()) {
            byDay.computeIfAbsent(toLocalDateTime(entry.dt(), zoneOffset).toLocalDate(), key -> new ArrayList<>())
                    .add(entry);
        }

        List<DailyForecast> daily = new ArrayList<>();
        byDay.forEach((date, entries) -> daily.add(buildDailyForecast(date, entries, sunrise, sunset, zoneOffset, units)));

        return new ForecastData(city, response.city().country(), units, PROVIDER_NAME, hourly, daily);
    }

    private DailyForecast buildDailyForecast(
            LocalDate date,
            List<OpenWeatherMapForecastResponse.Entry> entries,
            LocalDateTime sunrise,
            LocalDateTime sunset,
            ZoneOffset zoneOffset,
            Units units) {
        double temperatureMax = entries.stream()
                .mapToDouble(entry -> UnitConverter.kelvinToRequestedTemperature(entry.main().tempMax(), units))
                .max().orElse(0);
        double temperatureMin = entries.stream()
                .mapToDouble(entry -> UnitConverter.kelvinToRequestedTemperature(entry.main().tempMin(), units))
                .min().orElse(0);
        double windSpeedMax = entries.stream()
                .mapToDouble(entry -> entry.wind() != null
                        ? UnitConverter.metersPerSecondToRequestedSpeed(entry.wind().speed(), units) : 0)
                .max().orElse(0);
        int precipitationProbabilityMax = entries.stream()
                .mapToInt(entry -> (int) Math.round(entry.pop() * 100))
                .max().orElse(0);
        // The entry closest to local noon is the most representative single description for the
        // whole day (Open-Meteo instead gives one authoritative daily code; this endpoint doesn't).
        String description = entries.stream()
                .min((a, b) -> Long.compare(
                        Math.abs(toLocalDateTime(a.dt(), zoneOffset).toLocalTime().toSecondOfDay() - 43_200L),
                        Math.abs(toLocalDateTime(b.dt(), zoneOffset).toLocalTime().toSecondOfDay() - 43_200L)))
                .map(entry -> describe(entry.weather()))
                .orElse(UNKNOWN_DESCRIPTION);

        return new DailyForecast(
                date,
                temperatureMax,
                temperatureMin,
                description,
                sunrise,
                sunset,
                0, // No UV index on this free endpoint -- see class doc comment.
                precipitationProbabilityMax,
                windSpeedMax,
                null, // No marine data from OpenWeatherMap.
                null,
                precipitationProbabilityMax >= 50,
                UvRiskLabeler.label(0),
                OutdoorActivityScorer.label(
                        OutdoorActivityScorer.score(temperatureMax, windSpeedMax, precipitationProbabilityMax, 0, units)),
                null,
                null
        );
    }

    private static LocalDateTime toLocalDateTime(long epochSeconds, ZoneOffset zoneOffset) {
        return Instant.ofEpochSecond(epochSeconds).atOffset(zoneOffset).toLocalDateTime();
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
