package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vidi.weather.dto.UserResponse;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.UserNotFoundException;
import com.vidi.weather.model.Role;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private User userWithId(long id, String email, Role role) {
        User user = new User(email, "hash", Units.METRIC).withRole(role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void listsEveryRegisteredUser() {
        when(userRepository.findAll()).thenReturn(List.of(
                userWithId(1L, "admin@example.com", Role.ADMIN),
                userWithId(2L, "someone@example.com", Role.USER)));

        List<UserResponse> result = adminUserService.listUsers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).email()).isEqualTo("admin@example.com");
        assertThat(result.get(0).role()).isEqualTo("admin");
        assertThat(result.get(1).role()).isEqualTo("user");
    }

    @Test
    void deletesAnotherUsersAccount() {
        User admin = userWithId(1L, "admin@example.com", Role.ADMIN);
        User target = userWithId(2L, "someone@example.com", Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        adminUserService.deleteUser(2L, admin);

        org.mockito.Mockito.verify(userRepository).delete(target);
    }

    @Test
    void refusesToDeleteYourOwnAccount() {
        User admin = userWithId(1L, "admin@example.com", Role.ADMIN);

        assertThatThrownBy(() -> adminUserService.deleteUser(1L, admin))
                .isInstanceOf(IllegalArgumentException.class);

        org.mockito.Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void throwsWhenDeletingAUserThatDoesNotExist() {
        User admin = userWithId(1L, "admin@example.com", Role.ADMIN);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.deleteUser(99L, admin))
                .isInstanceOf(UserNotFoundException.class);
    }
}
