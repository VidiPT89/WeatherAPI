package com.vidi.weather.service;

import com.vidi.weather.entity.User;
import com.vidi.weather.exception.EmailAlreadyRegisteredException;
import com.vidi.weather.exception.OAuthTokenInvalidException;
import com.vidi.weather.model.OAuthProvider;
import com.vidi.weather.model.Role;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.UserRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.email:ividi.dev@gmail.com}") String adminEmail) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
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

    /**
     * Resolves the {@link User} for a verified OAuth identity: an existing link by
     * {@code (provider, providerId)} wins outright; otherwise a LOCAL/other-provider account with
     * the same (provider-verified) email gets this identity linked onto it; otherwise a brand new
     * account is created. Microsoft identities can create a regular account using their signed
     * subject, but their email claim never authorizes linking or administrative privileges.
     */
    public User findOrCreateFromOAuth(OAuthProvider provider, String providerId, String email, boolean emailVerified) {
        Optional<User> existingLink = userRepository.findByProviderAndProviderId(provider, providerId);
        if (existingLink.isPresent()) {
            return existingLink.get();
        }

        boolean trustedEmail = emailVerified && provider != OAuthProvider.MICROSOFT;
        if (!trustedEmail && provider != OAuthProvider.MICROSOFT) {
            throw new OAuthTokenInvalidException(provider);
        }

        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            if (!trustedEmail) {
                throw new OAuthTokenInvalidException(provider);
            }
            return userRepository.save(byEmail.get().withOAuthLink(provider, providerId));
        }

        try {
            User user = User.oauth(email, provider, providerId, Units.METRIC)
                    .withRole(trustedEmail ? roleFor(email) : Role.USER);
            return userRepository.save(user);
        } catch (DataIntegrityViolationException raced) {
            // Two sign-ins with the same brand-new OAuth identity raced past the check above --
            // whichever inserted first wins, this call just needs to return that same row.
            return userRepository.findByProviderAndProviderId(provider, providerId).orElseThrow(() -> raced);
        }
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    public User updatePreferredUnits(User user, Units units) {
        return userRepository.save(user.withPreferredUnits(units));
    }

    /**
     * Self-service account deletion (required by the App Store). Favorites, history and refresh
     * tokens go with it via ON DELETE CASCADE. The admin account is refused for the same reason
     * {@link AdminUserService#deleteUser} refuses it: nothing could promote a new admin.
     */
    @Transactional
    public void deleteAccount(User user) {
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("The admin account cannot be deleted from the app.");
        }
        userRepository.deleteById(user.getId());
    }

    private Role roleFor(String email) {
        return email.equalsIgnoreCase(adminEmail) ? Role.ADMIN : Role.USER;
    }
}
