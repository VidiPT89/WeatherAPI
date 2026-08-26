package com.vidi.weather.provider;

import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;

/**
 * Strategy/Adapter contract implemented by every external weather source.
 * Each implementation normalizes its provider-specific response into {@link WeatherData}.
 */
public interface WeatherProvider {

    WeatherData fetchCurrentWeather(String city, Units units);

    /**
     * Fetches current weather directly by coordinates, bypassing name-based lookup entirely.
     * Used for GPS-based requests, where round-tripping through a reverse-geocoded city name
     * can fail (e.g. name-vs-name mismatches between the reverse-geocoding and weather-by-name
     * providers) even though the coordinates themselves are perfectly valid.
     */
    WeatherData fetchCurrentWeatherByCoordinates(double latitude, double longitude, String cityName, Units units);

    String getProviderName();
}
