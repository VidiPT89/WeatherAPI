package com.vidi.weather.controller;

import com.vidi.weather.dto.CitySuggestion;
import com.vidi.weather.dto.GeocodingSearchResponse;
import com.vidi.weather.provider.openmeteo.GeocodingResponse.GeocodingResult;
import com.vidi.weather.service.GeocodingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geocoding")
@Tag(name = "Geocoding", description = "City search autocomplete")
public class GeocodingController {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @GetMapping
    @Operation(summary = "Search candidate cities matching a query, for autocomplete")
    public ResponseEntity<GeocodingSearchResponse> searchCities(
            @RequestParam String query,
            @RequestParam(required = false) Integer limit) {

        if (query.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'query' must not be blank");
        }

        int resolvedLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<GeocodingResult> results = geocodingService.searchCities(query, resolvedLimit);
        List<CitySuggestion> suggestions = results.stream()
                .map(r -> new CitySuggestion(r.name(), r.country(), r.latitude(), r.longitude()))
                .toList();
        return ResponseEntity.ok(new GeocodingSearchResponse(query, suggestions));
    }
}
