package com.vidi.weather.provider.openmeteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Open-Meteo forecast series (wind, air, rain, daylight) the coast outlook combines with the sea. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoastWindResponse(
        Hourly hourly,
        Daily daily,
        @JsonProperty("utc_offset_seconds") int utcOffsetSeconds
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hourly(
            List<String> time,
            @JsonProperty("wind_speed_10m") List<Double> windSpeed,
            @JsonProperty("wind_direction_10m") List<Double> windDirection,
            @JsonProperty("wind_gusts_10m") List<Double> windGusts,
            @JsonProperty("temperature_2m") List<Double> temperature,
            @JsonProperty("precipitation_probability") List<Integer> precipitationProbability
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(List<String> time, List<String> sunrise, List<String> sunset) {
    }
}
