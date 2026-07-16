package com.vidi.weather.provider;

import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;

/**
 * Strategy/Adapter contract implemented by every external weather source.
 * Each implementation normalizes its provider-specific response into {@link WeatherData}.
 */
public interface WeatherProvider {

    WeatherData fetchCurrentWeather(String city, Units units);

    String getProviderName();
}
