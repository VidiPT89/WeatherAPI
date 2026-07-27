package com.vidi.weather.service;

import com.vidi.weather.dto.UserResponse;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.UserNotFoundException;
import com.vidi.weather.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the admin-only user-management endpoints. Gated at the URL level in
 * {@code SecurityConfig} ({@code /api/v1/admin/**} requires {@code ROLE_ADMIN}), not here --
 * this service assumes the caller is already authorized.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    /**
     * Deleting your own account here would leave the app without any admin (there is no
     * self-service way to promote a user back to admin), so it's refused outright.
     */
    @Transactional
    public void deleteUser(Long id, User caller) {
        if (id.equals(caller.getId())) {
            throw new IllegalArgumentException("You cannot delete your own admin account.");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.delete(user);
    }
}
