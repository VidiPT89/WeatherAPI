package com.vidi.weather.dto;

import java.util.List;

public record GeocodingSearchResponse(String query, List<CitySuggestion> results) {
}
