package com.vidi.weather.repository;

import com.vidi.weather.entity.SearchHistoryEntry;
import com.vidi.weather.entity.User;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SearchHistoryRepository extends JpaRepository<SearchHistoryEntry, Long> {

    List<SearchHistoryEntry> findByUserOrderBySearchedAtDesc(User user);

    @Query("""
            SELECT s.city AS city, COUNT(s) AS searchCount
            FROM SearchHistoryEntry s
            GROUP BY s.city
            ORDER BY COUNT(s) DESC
            """)
    List<CityCount> findCitiesOrderedBySearchCountDesc(Pageable pageable);

    interface CityCount {
        String getCity();

        Long getSearchCount();
    }
}
