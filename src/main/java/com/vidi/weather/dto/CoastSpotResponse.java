package com.vidi.weather.dto;

import com.vidi.weather.model.CoastSpot;

/** {@code distanceKm} is only set when the caller sent its position. */
public record CoastSpotResponse(
        String id,
        String name,
        String area,
        double latitude,
        double longitude,
        Integer facingDegrees,
        Double distanceKm
) {

    public static CoastSpotResponse from(CoastSpot spot, Double distanceKm) {
        return new CoastSpotResponse(spot.id(), spot.name(), spot.area(), spot.latitude(), spot.longitude(),
                spot.facingDegrees(), distanceKm == null ? null : Math.round(distanceKm * 10) / 10.0);
    }
}
