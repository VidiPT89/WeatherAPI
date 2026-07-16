package com.vidi.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidi.weather.entity.SearchHistoryEntry;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SearchHistoryRepositoryTest {

    @Autowired
    private SearchHistoryRepository searchHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndListsHistoryNewestFirst() {
        User user = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        searchHistoryRepository.saveAndFlush(new SearchHistoryEntry(user, "Lisboa", Units.METRIC));
        searchHistoryRepository.saveAndFlush(new SearchHistoryEntry(user, "Porto", Units.IMPERIAL));

        List<SearchHistoryEntry> history = searchHistoryRepository.findByUserOrderBySearchedAtDesc(user);

        assertThat(history).extracting(SearchHistoryEntry::getCity).containsExactly("Porto", "Lisboa");
    }

    private String uniqueEmail() {
        return "hist-%s@example.com".formatted(UUID.randomUUID());
    }
}
