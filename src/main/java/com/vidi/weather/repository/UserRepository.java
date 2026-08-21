package com.vidi.weather.repository;

import com.vidi.weather.entity.User;
import com.vidi.weather.model.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);
}
