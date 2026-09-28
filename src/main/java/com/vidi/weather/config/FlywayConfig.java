package com.vidi.weather.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    /**
     * Skips migrations when the database can't be reached at startup instead of failing the whole
     * boot, so weather lookups (which never touch the database) keep working through a database
     * outage. The schema is already in place on any database this app has run against before;
     * pending migrations run on the next start with a reachable database. A reachable database
     * whose migration fails still aborts startup, as it should.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            try (Connection ignored = dataSource.getConnection()) {
                // Reachable -- fall through to the normal migration below.
            } catch (SQLException unreachable) {
                log.error("Database unreachable at startup, skipping Flyway migrations; accounts, history "
                        + "and favorites will fail until it is back: {}", unreachable.getMessage());
                return;
            }
            flyway.migrate();
        };
    }
}
