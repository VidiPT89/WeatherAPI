package com.vidi.weather.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Probed once a day by the repository's health-check workflow, which opens an issue while this
 * answers anything but 200. The weather endpoints keep working through a database outage, so
 * without this a dead database would go unnoticed until someone tried to sign in.
 *
 * <p>Deliberately not called by the keep-alive ping: every call opens a database connection,
 * which wakes a scale-to-zero database and bills compute for its idle timeout.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Liveness of the API and its database")
public class HealthController {

    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    @Operation(summary = "200 when the API and its database are up, 503 when the database is unreachable")
    public ResponseEntity<Map<String, String>> health() {
        boolean databaseUp;
        try (Connection connection = dataSource.getConnection()) {
            databaseUp = connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException unreachable) {
            databaseUp = false;
        }

        HttpStatus status = databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of(
                "status", databaseUp ? "UP" : "DEGRADED",
                "database", databaseUp ? "UP" : "DOWN"));
    }
}
