package com.vidi.weather.service;

import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.provider.WeatherProvider;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class WeatherAggregatorService {

    private final WeatherProvider weatherProvider;
    private final CacheService cacheService;

    public WeatherAggregatorService(WeatherProvider weatherProvider, CacheService cacheService) {
        this.weatherProvider = weatherProvider;
        this.cacheService = cacheService;
    }

    public WeatherResult getCurrentWeather(String city, Units units) {
        Optional<WeatherData> cached = cacheService.get(city, units);
        if (cached.isPresent()) {
            return new WeatherResult(cached.get(), true);
        }

        WeatherData fresh = weatherProvider.fetchCurrentWeather(city, units);
        cacheService.put(city, units, fresh);
        return new WeatherResult(fresh, false);
    }
}
