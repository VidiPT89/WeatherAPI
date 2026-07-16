package com.vidi.weather.dto;

public record StatsResponse(
        long totalUsers,
        long totalSearches,
        long totalFavorites,
        String mostSearchedCity,
        long mostSearchedCityCount,
        CacheStats cache
) {
    public record CacheStats(long hitCount, long missCount, double hitRate) {
    }
}
