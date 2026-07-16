package com.vidi.weather.model;

public enum Units {
    METRIC,
    IMPERIAL;

    public static Units fromString(String value) {
        if (value == null || value.isBlank()) {
            return METRIC;
        }
        try {
            return Units.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid units value: '%s'. Allowed values: metric, imperial".formatted(value));
        }
    }
}
