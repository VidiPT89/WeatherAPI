package com.vidi.weather.model;

/**
 * A beach the coast outlook knows about. {@code facingDegrees} is the compass direction the beach
 * looks out to sea (270 = faces west); it decides whether a given swell reaches the beach and
 * whether the wind is blowing offshore or onshore there. It is {@code null} for an arbitrary
 * location the user picked, whose orientation isn't known.
 */
public record CoastSpot(
        String id,
        String name,
        String area,
        double latitude,
        double longitude,
        Integer facingDegrees
) {
}
