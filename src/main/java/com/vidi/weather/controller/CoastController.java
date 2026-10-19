package com.vidi.weather.controller;

import com.vidi.weather.dto.CoastOutlookResponse;
import com.vidi.weather.dto.CoastSpotResponse;
import com.vidi.weather.exception.CityNotFoundException;
import com.vidi.weather.model.CoastSpot;
import com.vidi.weather.model.Units;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.CoastOutlookService;
import com.vidi.weather.service.CoastSpotCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coast")
@Tag(name = "Coast", description = "Beach spots and their surf, fishing and beach-day outlook")
public class CoastController {

    private static final int MAX_NAME_LENGTH = 100;

    private final CoastSpotCatalog catalog;
    private final CoastOutlookService outlookService;

    public CoastController(CoastSpotCatalog catalog, CoastOutlookService outlookService) {
        this.catalog = catalog;
        this.outlookService = outlookService;
    }

    @GetMapping("/spots")
    @Operation(summary = "List the known beach spots, nearest first when lat/lon are given (works anonymously)")
    public ResponseEntity<List<CoastSpotResponse>> getSpots(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {

        if (lat == null && lon == null) {
            return ResponseEntity.ok(catalog.all().stream().map(spot -> CoastSpotResponse.from(spot, null)).toList());
        }
        validateCoordinates(lat, lon);
        return ResponseEntity.ok(catalog.nearest(lat, lon).stream()
                .map(spot -> CoastSpotResponse.from(spot, CoastSpotCatalog.distanceKm(lat, lon, spot)))
                .toList());
    }

    @GetMapping("/outlook")
    @Operation(summary = "Get the hourly sea/wind/tide outlook and the best surf, fishing and beach windows "
            + "for a known spot (spot=id) or any coastal point (lat, lon, name) -- works anonymously; "
            + "an arbitrary point has no beach orientation, so its wind is not classed as offshore/onshore")
    public ResponseEntity<CoastOutlookResponse> getOutlook(
            @RequestParam(required = false) String spot,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String units,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        CoastSpot target;
        if (spot != null && !spot.isBlank()) {
            target = catalog.find(spot.trim()).orElseThrow(() -> new CityNotFoundException(spot));
        } else {
            validateCoordinates(lat, lon);
            String label = name == null || name.isBlank() ? "%.3f, %.3f".formatted(lat, lon) : name.trim();
            if (label.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("Query parameter 'name' must be at most 100 characters");
            }
            target = new CoastSpot(null, label, null, lat, lon, null);
        }

        Units parsedUnits = units != null
                ? Units.fromString(units)
                : principal != null ? principal.getUser().getPreferredUnits() : Units.METRIC;
        return ResponseEntity.ok(CoastOutlookResponse.from(outlookService.getOutlook(target), parsedUnits));
    }

    private static void validateCoordinates(Double lat, Double lon) {
        if (lat == null || lon == null) {
            throw new IllegalArgumentException("Give either 'spot', or both 'lat' and 'lon'");
        }
        if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Query parameter 'lat' must be between -90 and 90");
        }
        if (Double.isNaN(lon) || Double.isInfinite(lon) || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Query parameter 'lon' must be between -180 and 180");
        }
    }
}
