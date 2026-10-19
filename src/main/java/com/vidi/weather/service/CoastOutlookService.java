package com.vidi.weather.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.vidi.weather.model.CoastOutlookData;
import com.vidi.weather.model.CoastOutlookResult;
import com.vidi.weather.model.CoastSpot;
import com.vidi.weather.provider.CoastOutlookClient;
import com.vidi.weather.provider.openmeteo.CoastMarineResponse;
import com.vidi.weather.provider.openmeteo.CoastWindResponse;
import com.vidi.weather.util.CoastOutlookBuilder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The coast outlook for a spot: both Open-Meteo series behind the shared circuit breaker/retry,
 * cached per spot in metric (units are converted at the edge, so one entry serves both).
 */
@Service
public class CoastOutlookService {

    private final CoastOutlookClient client;
    private final ProviderResilienceExecutor resilienceExecutor;
    private final Cache<String, CoastOutlookData> cache;
    private final Clock clock;

    @Autowired
    public CoastOutlookService(
            CoastOutlookClient client,
            ProviderResilienceExecutor resilienceExecutor,
            Cache<String, CoastOutlookData> coastOutlookCache) {
        this(client, resilienceExecutor, coastOutlookCache, Clock.systemUTC());
    }

    CoastOutlookService(
            CoastOutlookClient client,
            ProviderResilienceExecutor resilienceExecutor,
            Cache<String, CoastOutlookData> coastOutlookCache,
            Clock clock) {
        this.client = client;
        this.resilienceExecutor = resilienceExecutor;
        this.cache = coastOutlookCache;
        this.clock = clock;
    }

    public CoastOutlookResult getOutlook(CoastSpot spot) {
        String key = cacheKey(spot);
        CoastOutlookData cached = cache.getIfPresent(key);
        if (cached != null) {
            return new CoastOutlookResult(cached, true);
        }

        CoastMarineResponse sea = resilienceExecutor.execute(CoastOutlookClient.PROVIDER_NAME,
                () -> client.fetchSea(spot.latitude(), spot.longitude()));
        CoastWindResponse wind = resilienceExecutor.execute(CoastOutlookClient.PROVIDER_NAME,
                () -> client.fetchWind(spot.latitude(), spot.longitude()));

        LocalDateTime localNow = LocalDateTime.ofInstant(
                Instant.now(clock), ZoneOffset.ofTotalSeconds(wind.utcOffsetSeconds()));
        CoastOutlookData fresh = CoastOutlookBuilder.build(spot, CoastOutlookClient.PROVIDER_NAME, sea, wind, localNow);
        cache.put(key, fresh);
        return new CoastOutlookResult(fresh, false);
    }

    private static String cacheKey(CoastSpot spot) {
        return spot.id() != null
                ? spot.id()
                : "@%.3f,%.3f".formatted(spot.latitude(), spot.longitude());
    }
}
