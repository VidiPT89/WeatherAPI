package com.vidi.weather.model;

import java.util.List;

/** Everything the coast screen shows for one spot, in metric units and the spot's local time. */
public record CoastOutlookData(
        CoastSpot spot,
        String provider,
        int utcOffsetSeconds,
        List<CoastHour> hours,
        List<TideEvent> tideEvents,
        List<CoastDay> days
) {
}
