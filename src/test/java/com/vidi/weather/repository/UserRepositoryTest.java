package com.vidi.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
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
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        String email = uniqueEmail();
        userRepository.save(new User(email, "hash", Units.METRIC));

        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void existsByEmailReturnsFalseForUnknownEmail() {
        assertThat(userRepository.existsByEmail(uniqueEmail())).isFalse();
    }

    @Test
    void existsByEmailReturnsTrueOnceUserIsSaved() {
        String email = uniqueEmail();
        userRepository.save(new User(email, "hash", Units.METRIC));

        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void enforcesUniqueEmailConstraint() {
        String email = uniqueEmail();
        userRepository.saveAndFlush(new User(email, "hash", Units.METRIC));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User(email, "other-hash", Units.METRIC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String uniqueEmail() {
        return "user-%s@example.com".formatted(UUID.randomUUID());
    }
}
