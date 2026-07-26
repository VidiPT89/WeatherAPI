package com.vidi.weather.provider.openmeteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarineResponse(Hourly hourly, Daily daily) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hourly(
            List<String> time,
            @JsonProperty("wave_height") List<Double> waveHeight,
            @JsonProperty("wave_direction") List<Double> waveDirection,
            @JsonProperty("wave_period") List<Double> wavePeriod,
            @JsonProperty("sea_surface_temperature") List<Double> seaSurfaceTemperature,
            @JsonProperty("sea_level_height_msl") List<Double> seaLevelHeightMsl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(
            List<String> time,
            @JsonProperty("wave_height_max") List<Double> waveHeightMax,
            @JsonProperty("wave_period_max") List<Double> wavePeriodMax
    ) {
    }
}
