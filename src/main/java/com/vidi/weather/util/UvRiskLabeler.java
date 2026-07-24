package com.vidi.weather.util;

/**
 * Maps a numeric UV index to the WHO's standard risk category.
 * Reference: https://www.who.int/news-room/questions-and-answers/item/radiation-the-ultraviolet-(uv)-index
 */
public final class UvRiskLabeler {

    private UvRiskLabeler() {
    }

    public static String label(double uvIndexMax) {
        if (uvIndexMax < 3) {
            return "Low";
        } else if (uvIndexMax < 6) {
            return "Moderate";
        } else if (uvIndexMax < 8) {
            return "High";
        } else if (uvIndexMax < 11) {
            return "Very High";
        }
        return "Extreme";
    }
}
