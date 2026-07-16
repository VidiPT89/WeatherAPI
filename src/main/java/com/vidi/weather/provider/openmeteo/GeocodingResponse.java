package com.vidi.weather.provider.openmeteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeocodingResponse(List<GeocodingResult> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeocodingResult(String name, String country, double latitude, double longitude) {
    }
}
