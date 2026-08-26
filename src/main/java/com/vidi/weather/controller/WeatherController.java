package com.vidi.weather.controller;

import com.vidi.weather.dto.FavoriteRequest;
import com.vidi.weather.dto.FavoriteResponse;
import com.vidi.weather.dto.ForecastWeatherResponse;
import com.vidi.weather.dto.MarineConditionsResponse;
import com.vidi.weather.dto.SearchHistoryResponse;
import com.vidi.weather.dto.WeatherInsightsResponse;
import com.vidi.weather.dto.WeatherResponse;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.model.ForecastResult;
import com.vidi.weather.model.MarineResult;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherInsightsData;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.FavoriteService;
import com.vidi.weather.service.ForecastService;
import com.vidi.weather.service.GeocodingService;
import com.vidi.weather.service.MarineService;
import com.vidi.weather.service.SearchHistoryService;
import com.vidi.weather.service.WeatherAggregatorService;
import com.vidi.weather.service.WeatherInsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
@Tag(name = "Weather", description = "Current weather, search history and favorites")
public class WeatherController {

    private final WeatherAggregatorService weatherAggregatorService;
    private final ForecastService forecastService;
    private final MarineService marineService;
    private final WeatherInsightsService weatherInsightsService;
    private final SearchHistoryService searchHistoryService;
    private final FavoriteService favoriteService;
    private final GeocodingService geocodingService;

    public WeatherController(
            WeatherAggregatorService weatherAggregatorService,
            ForecastService forecastService,
            MarineService marineService,
            WeatherInsightsService weatherInsightsService,
            SearchHistoryService searchHistoryService,
            FavoriteService favoriteService,
            GeocodingService geocodingService) {
        this.weatherAggregatorService = weatherAggregatorService;
        this.forecastService = forecastService;
        this.marineService = marineService;
        this.weatherInsightsService = weatherInsightsService;
        this.searchHistoryService = searchHistoryService;
        this.favoriteService = favoriteService;
        this.geocodingService = geocodingService;
    }

    @GetMapping
    @Operation(summary = "Get current weather for a city (works anonymously; searches are only recorded to "
            + "history when called with a valid access token)")
    public ResponseEntity<WeatherResponse> getCurrentWeather(
            @RequestParam String city,
            @RequestParam(required = false) String units,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        if (city.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'city' must not be blank");
        }

        Units parsedUnits = resolveUnits(units, principal);

        WeatherResult result = weatherAggregatorService.getCurrentWeather(city, parsedUnits);
        if (principal != null) {
            searchHistoryService.record(principal.getUser(), city, parsedUnits);
        }
        return ResponseEntity.ok(WeatherResponse.from(result));
    }

    @GetMapping("/forecast")
    @Operation(summary = "Get hourly and daily forecast for a city (works anonymously)")
    public ResponseEntity<ForecastWeatherResponse> getForecast(
            @RequestParam String city,
            @RequestParam(required = false) String units,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        if (city.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'city' must not be blank");
        }

        Units parsedUnits = resolveUnits(units, principal);
        ForecastResult result = forecastService.getForecast(city, parsedUnits);
        return ResponseEntity.ok(ForecastWeatherResponse.from(result));
    }

    @GetMapping("/marine")
    @Operation(summary = "Get current sea conditions (water temperature, wave height/direction/period) for a "
            + "coastal city (works anonymously) — fields come back null for cities with no nearby marine data")
    public ResponseEntity<MarineConditionsResponse> getMarineConditions(
            @RequestParam String city,
            @RequestParam(required = false) String units,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        if (city.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'city' must not be blank");
        }

        Units parsedUnits = resolveUnits(units, principal);
        MarineResult result = marineService.getMarineConditions(city, parsedUnits);
        return ResponseEntity.ok(MarineConditionsResponse.from(result));
    }

    @GetMapping("/insights")
    @Operation(summary = "Get derived weather insights (moon phase, UV risk, outdoor-activity score, fishing "
            + "conditions) for a city (works anonymously) — fishingConditionLabel comes back null for cities "
            + "with no marine data")
    public ResponseEntity<WeatherInsightsResponse> getWeatherInsights(
            @RequestParam String city,
            @RequestParam(required = false) String units,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        if (city.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'city' must not be blank");
        }

        Units parsedUnits = resolveUnits(units, principal);
        WeatherInsightsData data = weatherInsightsService.getInsights(city, parsedUnits);
        return ResponseEntity.ok(WeatherInsightsResponse.from(data));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Get current weather for the caller's GPS coordinates, reverse-geocoded to a city "
            + "(works anonymously; searches are only recorded to history when called with a valid access token)")
    public ResponseEntity<WeatherResponse> getCurrentWeatherNearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false) String units,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Query parameter 'lat' must be between -90 and 90");
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Query parameter 'lon' must be between -180 and 180");
        }

        Units parsedUnits = resolveUnits(units, principal);

        GeocodingResult location = geocodingService.reverseGeocode(lat, lon)
                .orElseThrow(() -> new CityNotFoundException("coordinates %.4f,%.4f".formatted(lat, lon)));

        WeatherResult result = weatherAggregatorService.getCurrentWeatherByCoordinates(
                location.latitude(), location.longitude(), location.name(), parsedUnits);
        if (principal != null) {
            searchHistoryService.record(principal.getUser(), location.name(), parsedUnits);
        }
        return ResponseEntity.ok(WeatherResponse.from(result));
    }

    private static Units resolveUnits(String units, AuthenticatedUser principal) {
        if (units != null) {
            return Units.fromString(units);
        }
        return principal != null ? principal.getUser().getPreferredUnits() : Units.METRIC;
    }

    @GetMapping("/history")
    @Operation(summary = "List the authenticated user's past searches")
    public ResponseEntity<List<SearchHistoryResponse>> getHistory(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(searchHistoryService.listForUser(principal.getUser()));
    }

    @DeleteMapping("/history/{id}")
    @Operation(summary = "Remove a single entry from the authenticated user's search history")
    public ResponseEntity<Void> deleteHistoryEntry(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        searchHistoryService.delete(principal.getUser(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history")
    @Operation(summary = "Clear the authenticated user's entire search history")
    public ResponseEntity<Void> clearHistory(@AuthenticationPrincipal AuthenticatedUser principal) {
        searchHistoryService.deleteAll(principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/favorites")
    @Operation(summary = "List the authenticated user's favorite cities")
    public ResponseEntity<List<FavoriteResponse>> getFavorites(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(favoriteService.listForUser(principal.getUser()));
    }

    @PostMapping("/favorites")
    @Operation(summary = "Add a city to the authenticated user's favorites")
    public ResponseEntity<FavoriteResponse> addFavorite(
            @Valid @RequestBody FavoriteRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        FavoriteResponse favorite = favoriteService.add(principal.getUser(), request.city());
        return ResponseEntity.status(HttpStatus.CREATED).body(favorite);
    }

    @DeleteMapping("/favorites")
    @Operation(summary = "Remove a city from the authenticated user's favorites")
    public ResponseEntity<Void> removeFavorite(
            @RequestParam String city, @AuthenticationPrincipal AuthenticatedUser principal) {
        favoriteService.remove(principal.getUser(), city);
        return ResponseEntity.noContent().build();
    }
}
