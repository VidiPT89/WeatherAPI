package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vidi.weather.entity.User;
import com.vidi.weather.exception.EmailAlreadyRegisteredException;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registersNewUserWithEncodedPasswordAndDefaultUnits() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.register("test@example.com", "password123");

        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(result.getPreferredUnits()).isEqualTo(Units.METRIC);
    }

    @Test
    void throwsWhenEmailAlreadyRegistered() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("test@example.com", "password123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updatePreferredUnitsPersistsANewImmutableCopy() {
        User existing = new User("test@example.com", "hash", Units.METRIC);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updatePreferredUnits(existing, Units.IMPERIAL);

        assertThat(updated.getPreferredUnits()).isEqualTo(Units.IMPERIAL);
        assertThat(updated).isNotSameAs(existing);
        assertThat(existing.getPreferredUnits()).isEqualTo(Units.METRIC);
    }

    @Test
    void findByEmailThrowsWhenUserIsMissing() {
        when(userRepository.findByEmail(eq("missing@example.com"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail("missing@example.com"))
                .isInstanceOf(IllegalStateException.class);
    }
}
