package com.vidi.weather.service;

import com.vidi.weather.entity.User;
import com.vidi.weather.exception.EmailAlreadyRegisteredException;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        // Same exists-check-then-save gap as FavoriteService.add: the DB's unique constraint on
        // email is the real backstop for two concurrent registrations of the same address.
        try {
            User user = new User(email, passwordEncoder.encode(rawPassword), Units.METRIC);
            return userRepository.save(user);
        } catch (DataIntegrityViolationException alreadyExists) {
            throw new EmailAlreadyRegisteredException(email);
        }
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    public User updatePreferredUnits(User user, Units units) {
        return userRepository.save(user.withPreferredUnits(units));
    }
}
