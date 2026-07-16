package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vidi.weather.dto.StatsResponse;
import com.vidi.weather.model.Units;
import com.vidi.weather.model.WeatherData;
import com.vidi.weather.repository.FavoriteRepository;
import com.vidi.weather.repository.SearchHistoryRepository;
import com.vidi.weather.repository.SearchHistoryRepository.CityCount;
import com.vidi.weather.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Test
    void aggregatesCountsAndMostSearchedCity() {
        when(userRepository.count()).thenReturn(5L);
        when(searchHistoryRepository.count()).thenReturn(42L);
        when(favoriteRepository.count()).thenReturn(7L);
        when(searchHistoryRepository.findCitiesOrderedBySearchCountDesc(any()))
                .thenReturn(List.of(cityCount("Lisboa", 10L)));

        Cache<String, WeatherData> cache = Caffeine.newBuilder().recordStats().build();
        cache.put("lisboa:metric", sampleWeatherData());
        cache.getIfPresent("lisboa:metric");
        cache.getIfPresent("nowhere:metric");

        StatsService statsService = new StatsService(userRepository, searchHistoryRepository, favoriteRepository, cache);
        StatsResponse stats = statsService.getStats();

        assertThat(stats.totalUsers()).isEqualTo(5L);
        assertThat(stats.totalSearches()).isEqualTo(42L);
        assertThat(stats.totalFavorites()).isEqualTo(7L);
        assertThat(stats.mostSearchedCity()).isEqualTo("Lisboa");
        assertThat(stats.mostSearchedCityCount()).isEqualTo(10L);
        assertThat(stats.cache().hitCount()).isEqualTo(1L);
        assertThat(stats.cache().missCount()).isEqualTo(1L);
    }

    @Test
    void returnsNullMostSearchedCity_whenNoSearchesRecordedYet() {
        when(userRepository.count()).thenReturn(0L);
        when(searchHistoryRepository.count()).thenReturn(0L);
        when(favoriteRepository.count()).thenReturn(0L);
        when(searchHistoryRepository.findCitiesOrderedBySearchCountDesc(any())).thenReturn(List.of());

        Cache<String, WeatherData> cache = Caffeine.newBuilder().recordStats().build();
        StatsService statsService = new StatsService(userRepository, searchHistoryRepository, favoriteRepository, cache);

        StatsResponse stats = statsService.getStats();

        assertThat(stats.mostSearchedCity()).isNull();
        assertThat(stats.mostSearchedCityCount()).isZero();
    }

    private CityCount cityCount(String city, long count) {
        return new CityCount() {
            @Override
            public String getCity() {
                return city;
            }

            @Override
            public Long getSearchCount() {
                return count;
            }
        };
    }

    private WeatherData sampleWeatherData() {
        return new WeatherData(
                "Lisboa", "Portugal", 22.5, 21.8, 65, 12.3, "Clear sky", Units.METRIC, "open-meteo", Instant.now());
    }
}
