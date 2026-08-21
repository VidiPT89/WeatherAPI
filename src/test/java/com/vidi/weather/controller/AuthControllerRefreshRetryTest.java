package com.vidi.weather.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vidi.weather.dto.RefreshRequest;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import com.vidi.weather.security.JwtService;
import com.vidi.weather.security.RefreshTokenService;
import com.vidi.weather.security.oauth.OidcIdTokenVerifier;
import com.vidi.weather.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * Unit-level (no Spring context) coverage for the retry-on-optimistic-lock-conflict wrapper
 * around {@link RefreshTokenService#rotate}. The full concurrent-race scenario this guards
 * against is exercised more realistically by {@link com.vidi.weather.security.RefreshTokenServiceTest}
 * ; this just proves the controller actually retries once on the specific exception type and
 * propagates a second failure instead of retrying forever.
 */
class AuthControllerRefreshRetryTest {

    private final UserService userService = mock(UserService.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final OidcIdTokenVerifier oidcIdTokenVerifier = mock(OidcIdTokenVerifier.class);
    private final AuthController controller =
            new AuthController(userService, authenticationManager, jwtService, refreshTokenService, oidcIdTokenVerifier);

    private final User user = new User("test@example.com", "hash", Units.METRIC);

    @Test
    void retriesOnceWhenTheFirstRotationLosesTheOptimisticLockRace() {
        RefreshTokenService.RotationResult winnerResult = new RefreshTokenService.RotationResult(user, "new-raw-token");
        when(refreshTokenService.rotate("raw"))
                .thenThrow(new OptimisticLockingFailureException("lost the race"))
                .thenReturn(winnerResult);
        when(jwtService.generateToken(user)).thenReturn("jwt");
        when(jwtService.expirationSeconds()).thenReturn(3600L);

        ResponseEntity<com.vidi.weather.dto.AuthResponse> response = controller.refresh(new RefreshRequest("raw"));

        assertThat(response.getBody().refreshToken()).isEqualTo("new-raw-token");
        verify(refreshTokenService, times(2)).rotate("raw");
    }

    @Test
    void propagatesTheFailure_whenBothTheOriginalAndTheRetryLoseTheRace() {
        when(refreshTokenService.rotate(any())).thenThrow(new OptimisticLockingFailureException("lost the race"));

        org.junit.jupiter.api.Assertions.assertThrows(
                OptimisticLockingFailureException.class, () -> controller.refresh(new RefreshRequest("raw")));

        verify(refreshTokenService, times(2)).rotate("raw");
    }
}
