package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.vidi.weather.model.CoastSpot;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoastSpotCatalogTest {

    private final CoastSpotCatalog catalog = new CoastSpotCatalog();

    @Test
    void everySpotHasAUniqueIdAPlausiblePositionAndAFacing() {
        List<CoastSpot> spots = catalog.all();

        assertThat(spots).isNotEmpty();
        assertThat(new HashSet<>(spots.stream().map(CoastSpot::id).toList())).hasSize(spots.size());
        assertThat(spots).allSatisfy(spot -> {
            assertThat(spot.latitude()).isBetween(36.9, 42.2);   // mainland Portugal
            assertThat(spot.longitude()).isBetween(-9.6, -7.3);
            assertThat(spot.facingDegrees()).isBetween(0, 359);
        });
    }

    @Test
    void ordersSpotsByDistance() {
        // Cascais train station.
        List<CoastSpot> nearest = catalog.nearest(38.6979, -9.4215);

        assertThat(nearest.get(0).id()).isIn("guincho", "carcavelos");
        assertThat(nearest.get(nearest.size() - 1).id()).isIn("viana-cabedelo", "tavira");
    }

    @Test
    void findsASpotById() {
        assertThat(catalog.find("sagres-tonel")).get().extracting(CoastSpot::area).isEqualTo("Sagres");
        assertThat(catalog.find("nowhere")).isEmpty();
    }

    @Test
    void measuresDistanceInKilometres() {
        CoastSpot carcavelos = catalog.find("carcavelos").orElseThrow();
        // Lisbon (Praça do Comércio) to Carcavelos beach is roughly 17 km in a straight line.
        assertThat(CoastSpotCatalog.distanceKm(38.7075, -9.1364, carcavelos)).isCloseTo(17.8, within(1.5));
    }
}
