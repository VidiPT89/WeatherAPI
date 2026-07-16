package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vidi.weather.entity.SearchHistoryEntry;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.SearchHistoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @InjectMocks
    private SearchHistoryService searchHistoryService;

    private final User user = new User("test@example.com", "hash", Units.METRIC);

    @Test
    void recordsSearchForAuthenticatedUser() {
        searchHistoryService.record(user, "Lisboa", Units.METRIC);

        verify(searchHistoryRepository).save(argThat(entry ->
                entry.getCity().equals("Lisboa")
                        && entry.getUnits() == Units.METRIC
                        && entry.getUser() == user));
    }

    @Test
    void listsHistoryMappedToResponseDto() {
        when(searchHistoryRepository.findByUserOrderBySearchedAtDesc(user))
                .thenReturn(List.of(new SearchHistoryEntry(user, "Lisboa", Units.METRIC)));

        List<com.vidi.weather.dto.SearchHistoryResponse> result = searchHistoryService.listForUser(user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).city()).isEqualTo("Lisboa");
        assertThat(result.get(0).units()).isEqualTo("metric");
    }
}
