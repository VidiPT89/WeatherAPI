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

    @Test
    void deletesOnlyTheGivenEntryWhenItBelongsToTheUser() {
        User user = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        SearchHistoryEntry lisboa = searchHistoryRepository.saveAndFlush(new SearchHistoryEntry(user, "Lisboa", Units.METRIC));
        searchHistoryRepository.saveAndFlush(new SearchHistoryEntry(user, "Porto", Units.IMPERIAL));

        long deleted = searchHistoryRepository.deleteByUserAndId(user, lisboa.getId());

        assertThat(deleted).isEqualTo(1);
        assertThat(searchHistoryRepository.findByUserOrderBySearchedAtDesc(user))
                .extracting(SearchHistoryEntry::getCity)
                .containsExactly("Porto");
    }

    @Test
    void deletingAnEntryOwnedByAnotherUserDeletesNothing() {
        User owner = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        User other = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        SearchHistoryEntry entry = searchHistoryRepository.saveAndFlush(new SearchHistoryEntry(owner, "Lisboa", Units.METRIC));

        long deleted = searchHistoryRepository.deleteByUserAndId(other, entry.getId());

        assertThat(deleted).isEqualTo(0);
        assertThat(searchHistoryRepository.findByUserOrderBySearchedAtDesc(owner)).hasSize(1);
    }

    @Test
    void deleteByUserClearsEveryEntryForThatUserOnly() {
        User user = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        User other = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        searchHistoryRepository.saveAndFlush(new SearchHistoryEntry(user, "Lisboa", Units.METRIC));
        searchHistoryRepository.saveAndFlush(new SearchHistoryEntry(user, "Porto", Units.IMPERIAL));
        searchHistoryRepository.saveAndFlush(new SearchHistoryEntry(other, "Faro", Units.METRIC));

        long deleted = searchHistoryRepository.deleteByUser(user);

        assertThat(deleted).isEqualTo(2);
        assertThat(searchHistoryRepository.findByUserOrderBySearchedAtDesc(user)).isEmpty();
        assertThat(searchHistoryRepository.findByUserOrderBySearchedAtDesc(other)).hasSize(1);
    }

    private String uniqueEmail() {
        return "hist-%s@example.com".formatted(UUID.randomUUID());
    }
}
