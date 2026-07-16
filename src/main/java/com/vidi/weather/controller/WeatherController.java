package com.vidi.weather.controller;

import com.vidi.weather.dto.WeatherResponse;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.service.WeatherAggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
@Tag(name = "Weather", description = "Current weather aggregation endpoints")
public class WeatherController {

    private final WeatherAggregatorService weatherAggregatorService;

    public WeatherController(WeatherAggregatorService weatherAggregatorService) {
        this.weatherAggregatorService = weatherAggregatorService;
    }

    @GetMapping
    @Operation(summary = "Get current weather for a city")
    public ResponseEntity<WeatherResponse> getCurrentWeather(
            @RequestParam String city,
            @RequestParam(required = false, defaultValue = "metric") String units) {

        if (city.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'city' must not be blank");
        }

        Units parsedUnits = Units.fromString(units);
        WeatherResult result = weatherAggregatorService.getCurrentWeather(city, parsedUnits);
        return ResponseEntity.ok(WeatherResponse.from(result));
    }
}
