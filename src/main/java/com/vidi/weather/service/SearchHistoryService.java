package com.vidi.weather.service;

import com.vidi.weather.dto.SearchHistoryResponse;
import com.vidi.weather.entity.SearchHistoryEntry;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.SearchHistoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchHistoryService {

    private final SearchHistoryRepository searchHistoryRepository;

    public SearchHistoryService(SearchHistoryRepository searchHistoryRepository) {
        this.searchHistoryRepository = searchHistoryRepository;
    }

    public void record(User user, String city, Units units) {
        searchHistoryRepository.save(new SearchHistoryEntry(user, city, units));
    }

    public List<SearchHistoryResponse> listForUser(User user) {
        return searchHistoryRepository.findByUserOrderBySearchedAtDesc(user).stream()
                .map(entry -> new SearchHistoryResponse(
                        entry.getCity(), entry.getUnits().name().toLowerCase(), entry.getSearchedAt()))
                .toList();
    }
}
