package com.vidi.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vidi.weather.entity.Favorite;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FavoriteRepositoryTest {

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesFavoriteAndListsNewestFirst() {
        User user = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        favoriteRepository.saveAndFlush(new Favorite(user, "Lisboa"));
        favoriteRepository.saveAndFlush(new Favorite(user, "Porto"));

        List<Favorite> favorites = favoriteRepository.findByUserOrderByCreatedAtDesc(user);

        assertThat(favorites).extracting(Favorite::getCity).containsExactly("Porto", "Lisboa");
    }

    @Test
    void existsByUserAndCityIgnoreCaseIsCaseInsensitive() {
        User user = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        favoriteRepository.save(new Favorite(user, "Lisboa"));

        assertThat(favoriteRepository.existsByUserAndCityIgnoreCase(user, "lisboa")).isTrue();
    }

    @Test
    void enforcesUniqueUserCityConstraint() {
        User user = userRepository.save(new User(uniqueEmail(), "hash", Units.METRIC));
        favoriteRepository.saveAndFlush(new Favorite(user, "Lisboa"));

        assertThatThrownBy(() -> favoriteRepository.saveAndFlush(new Favorite(user, "Lisboa")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String uniqueEmail() {
        return "fav-%s@example.com".formatted(UUID.randomUUID());
    }
}
