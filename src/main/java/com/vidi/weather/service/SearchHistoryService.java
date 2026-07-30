package com.vidi.weather.service;

import com.vidi.weather.dto.SearchHistoryResponse;
import com.vidi.weather.entity.SearchHistoryEntry;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.SearchHistoryEntryNotFoundException;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.SearchHistoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                        entry.getId(), entry.getCity(), entry.getUnits().name().toLowerCase(), entry.getSearchedAt()))
                .toList();
    }

    /**
     * Scoping the delete to {@code user} as well as {@code id} means an id belonging to another
     * user 404s the same way a nonexistent one does, instead of leaking whether it exists.
     */
    @Transactional
    public void delete(User user, Long id) {
        long deleted = searchHistoryRepository.deleteByUserAndId(user, id);
        if (deleted == 0) {
            throw new SearchHistoryEntryNotFoundException(id);
        }
    }

    @Transactional
    public void deleteAll(User user) {
        searchHistoryRepository.deleteByUser(user);
    }
}
