package com.vidi.weather.provider.openmeteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Open-Meteo marine hourly series for the coast outlook, wider than {@link MarineResponse}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoastMarineResponse(Hourly hourly) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hourly(
            List<String> time,
            @JsonProperty("wave_height") List<Double> waveHeight,
            @JsonProperty("wave_period") List<Double> wavePeriod,
            @JsonProperty("wave_direction") List<Double> waveDirection,
            @JsonProperty("swell_wave_height") List<Double> swellWaveHeight,
            @JsonProperty("swell_wave_period") List<Double> swellWavePeriod,
            @JsonProperty("swell_wave_direction") List<Double> swellWaveDirection,
            @JsonProperty("sea_surface_temperature") List<Double> seaSurfaceTemperature,
            @JsonProperty("sea_level_height_msl") List<Double> seaLevelHeightMsl
    ) {
    }
}
