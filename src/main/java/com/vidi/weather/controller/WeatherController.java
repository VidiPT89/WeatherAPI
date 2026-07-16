package com.vidi.weather.controller;

import com.vidi.weather.dto.CompareResponse;
import com.vidi.weather.dto.FavoriteRequest;
import com.vidi.weather.dto.FavoriteResponse;
import com.vidi.weather.dto.SearchHistoryResponse;
import com.vidi.weather.dto.WeatherResponse;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherResult;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.FavoriteService;
import com.vidi.weather.service.SearchHistoryService;
import com.vidi.weather.service.WeatherAggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final SearchHistoryService searchHistoryService;
    private final FavoriteService favoriteService;

    public WeatherController(
            WeatherAggregatorService weatherAggregatorService,
            SearchHistoryService searchHistoryService,
            FavoriteService favoriteService) {
        this.weatherAggregatorService = weatherAggregatorService;
        this.searchHistoryService = searchHistoryService;
        this.favoriteService = favoriteService;
    }

    @GetMapping
    @Operation(summary = "Get current weather for a city")
    public ResponseEntity<WeatherResponse> getCurrentWeather(
            @RequestParam String city,
            @RequestParam(required = false) String units,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        if (city.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'city' must not be blank");
        }

        var user = principal.getUser();
        Units parsedUnits = units != null ? Units.fromString(units) : user.getPreferredUnits();

        WeatherResult result = weatherAggregatorService.getCurrentWeather(city, parsedUnits);
        searchHistoryService.record(user, city, parsedUnits);
        return ResponseEntity.ok(WeatherResponse.from(result));
    }

    @GetMapping("/compare")
    @Operation(summary = "Compare current weather for a city across all configured providers")
    public ResponseEntity<CompareResponse> compareProviders(
            @RequestParam String city,
            @RequestParam(required = false) String units,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        if (city.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'city' must not be blank");
        }

        Units parsedUnits = units != null ? Units.fromString(units) : principal.getUser().getPreferredUnits();
        return ResponseEntity.ok(weatherAggregatorService.compareProviders(city, parsedUnits));
    }

    @GetMapping("/history")
    @Operation(summary = "List the authenticated user's past searches")
    public ResponseEntity<List<SearchHistoryResponse>> getHistory(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(searchHistoryService.listForUser(principal.getUser()));
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
}
