package com.vidi.weather.repository;

import com.vidi.weather.entity.SearchHistoryEntry;
import com.vidi.weather.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryRepository extends JpaRepository<SearchHistoryEntry, Long> {

    List<SearchHistoryEntry> findByUserOrderBySearchedAtDesc(User user);
}
