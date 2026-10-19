package com.vidi.weather.service;

import com.vidi.weather.model.CoastSpot;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Hand-picked beaches along mainland Portugal, north to south. Coordinates sit on the beach and
 * every one was checked to return Open-Meteo marine data; {@code facingDegrees} is the direction
 * the beach looks out to sea, which is what lets two beaches sharing one offshore forecast (e.g.
 * Sagres' Tonel and Mareta) still get very different surf and wind verdicts.
 */
@Component
public class CoastSpotCatalog {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final List<CoastSpot> SPOTS = List.of(
            new CoastSpot("viana-cabedelo", "Cabedelo", "Viana do Castelo", 41.6800, -8.8330, 265),
            new CoastSpot("povoa-varzim", "Póvoa de Varzim", "Porto", 41.3790, -8.7680, 275),
            new CoastSpot("matosinhos", "Matosinhos", "Porto", 41.1770, -8.6960, 250),
            new CoastSpot("espinho", "Espinho", "Aveiro", 41.0070, -8.6490, 270),
            new CoastSpot("costa-nova", "Costa Nova", "Aveiro", 40.6140, -8.7560, 275),
            new CoastSpot("figueira-buarcos", "Buarcos", "Figueira da Foz", 40.1640, -8.8800, 225),
            new CoastSpot("sao-pedro-moel", "São Pedro de Moel", "Marinha Grande", 39.7590, -9.0320, 285),
            new CoastSpot("nazare-norte", "Praia do Norte", "Nazaré", 39.6060, -9.0900, 275),
            new CoastSpot("nazare", "Praia da Nazaré", "Nazaré", 39.5990, -9.0760, 245),
            new CoastSpot("baleal", "Baleal", "Peniche", 39.3760, -9.3400, 340),
            new CoastSpot("peniche-supertubos", "Supertubos", "Peniche", 39.3430, -9.3640, 220),
            new CoastSpot("ericeira-ribeira", "Ribeira d'Ilhas", "Ericeira", 38.9880, -9.4220, 280),
            new CoastSpot("ericeira-foz-lizandro", "Foz do Lizandro", "Ericeira", 38.9420, -9.4180, 270),
            new CoastSpot("guincho", "Guincho", "Cascais", 38.7310, -9.4760, 275),
            new CoastSpot("carcavelos", "Carcavelos", "Cascais", 38.6760, -9.3370, 190),
            new CoastSpot("costa-caparica", "Costa da Caparica", "Almada", 38.6400, -9.2420, 260),
            new CoastSpot("sesimbra", "Sesimbra", "Setúbal", 38.4410, -9.1030, 180),
            new CoastSpot("sines-sao-torpes", "São Torpes", "Sines", 37.9320, -8.8200, 230),
            new CoastSpot("milfontes", "Vila Nova de Milfontes", "Odemira", 37.7240, -8.7890, 265),
            new CoastSpot("odeceixe", "Odeceixe", "Aljezur", 37.4420, -8.8030, 280),
            new CoastSpot("arrifana", "Arrifana", "Aljezur", 37.2950, -8.8690, 290),
            new CoastSpot("sagres-tonel", "Tonel", "Sagres", 37.0040, -8.9470, 250),
            new CoastSpot("sagres-mareta", "Mareta", "Sagres", 37.0020, -8.9400, 175),
            new CoastSpot("lagos-meia-praia", "Meia Praia", "Lagos", 37.1080, -8.6600, 150),
            new CoastSpot("praia-rocha", "Praia da Rocha", "Portimão", 37.1170, -8.5360, 185),
            new CoastSpot("albufeira", "Praia dos Pescadores", "Albufeira", 37.0850, -8.2500, 185),
            new CoastSpot("faro", "Praia de Faro", "Faro", 37.0080, -7.9900, 190),
            new CoastSpot("tavira", "Ilha de Tavira", "Tavira", 37.1100, -7.6190, 150));

    public List<CoastSpot> all() {
        return SPOTS;
    }

    public Optional<CoastSpot> find(String id) {
        return SPOTS.stream().filter(spot -> spot.id().equals(id)).findFirst();
    }

    /** All spots, nearest first. */
    public List<CoastSpot> nearest(double latitude, double longitude) {
        return SPOTS.stream()
                .sorted(Comparator.comparingDouble(spot -> distanceKm(latitude, longitude, spot)))
                .toList();
    }

    /** Great-circle (haversine) distance. */
    public static double distanceKm(double latitude, double longitude, CoastSpot spot) {
        double dLat = Math.toRadians(spot.latitude() - latitude);
        double dLon = Math.toRadians(spot.longitude() - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(spot.latitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
