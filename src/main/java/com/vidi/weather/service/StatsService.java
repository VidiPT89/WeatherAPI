package com.vidi.weather.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.vidi.weather.dto.StatsResponse;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.repository.FavoriteRepository;
import com.vidi.weather.repository.SearchHistoryRepository;
import com.vidi.weather.repository.SearchHistoryRepository.CityCount;
import com.vidi.weather.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

    private final UserRepository userRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final FavoriteRepository favoriteRepository;
    private final Cache<String, WeatherData> weatherCache;

    public StatsService(
            UserRepository userRepository,
            SearchHistoryRepository searchHistoryRepository,
            FavoriteRepository favoriteRepository,
            Cache<String, WeatherData> weatherCache) {
        this.userRepository = userRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.favoriteRepository = favoriteRepository;
        this.weatherCache = weatherCache;
    }

    public StatsResponse getStats() {
        List<CityCount> topCity = searchHistoryRepository.findCitiesOrderedBySearchCountDesc(PageRequest.of(0, 1));
        String mostSearchedCity = topCity.isEmpty() ? null : topCity.get(0).getCity();
        long mostSearchedCityCount = topCity.isEmpty() ? 0 : topCity.get(0).getSearchCount();

        CacheStats cacheStats = weatherCache.stats();

        return new StatsResponse(
                userRepository.count(),
                searchHistoryRepository.count(),
                favoriteRepository.count(),
                mostSearchedCity,
                mostSearchedCityCount,
                new StatsResponse.CacheStats(cacheStats.hitCount(), cacheStats.missCount(), cacheStats.hitRate()));
    }
}
