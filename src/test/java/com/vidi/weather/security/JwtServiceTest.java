package com.vidi.weather.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "OCmPQ/BnxKOAKv/4OfYjssQOOCuKOVawTEHC/Cp1v0GIOthc1FlQ6a5hph1Eri+U";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60, 30));
    private final User user = new User("test@example.com", "hashed-password", Units.METRIC);

    @Test
    void generatesTokenThatResolvesBackToTheSameEmail() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo("test@example.com");
    }

    @Test
    void freshlyGeneratedTokenIsValid() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(user) + "tampered";

        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService(new JwtProperties(
                "eOa8DHc9c2Ep2X9K2t0hV6z1B0p3z0v1t5nQ3s9G8a4d0f7h6j5k4l3m2n1o0p9q", 60, 30));
        String token = otherService.generateToken(user);

        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void expirationSecondsMatchesConfiguredMinutes() {
        assertThat(jwtService.expirationSeconds()).isEqualTo(60 * 60L);
    }
}
