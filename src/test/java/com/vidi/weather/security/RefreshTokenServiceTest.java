package com.vidi.weather.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vidi.weather.entity.RefreshToken;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.InvalidRefreshTokenException;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.RefreshTokenRepository;
import com.vidi.weather.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private RefreshTokenService refreshTokenService;

    private final User user = withId(new User("test@example.com", "hash", Units.METRIC), 1L);
    private final AtomicLong idSequence = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository, new JwtProperties("secret", 60, 30));
    }

    @Test
    void issueSavesATokenAndReturnsTheRawValue() {
        stubSaveReturningSameEntityWithId();

        String raw = refreshTokenService.issue(user);

        assertThat(raw).isNotBlank();
    }

    @Test
    void rotatingAValidTokenReturnsANewPairForTheSameUser() {
        stubSaveReturningSameEntityWithId();
        RefreshToken current = activeToken();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(current));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("raw-token");

        assertThat(result.user()).isEqualTo(user);
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    void rotatingAnUnknownTokenThrows() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("does-not-exist"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotatingAnExpiredTokenThrows() {
        RefreshToken expired = new RefreshToken(1L, "hash", Instant.now().minus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotatingATokenThatWasAlreadyRotatedWithinTheGraceWindowStillSucceeds() {
        stubSaveReturningSameEntityWithId();
        RefreshToken alreadyRotated = activeToken().revokedBy(42L);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(alreadyRotated));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("recently-rotated");

        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    void rotatingATokenRotatedLongAgoThrows() {
        RefreshToken staleRotation = activeToken().revokedBy(42L);
        ReflectionTestUtils.setField(staleRotation, "revokedAt", Instant.now().minus(1, ChronoUnit.MINUTES));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(staleRotation));

        assertThatThrownBy(() -> refreshTokenService.rotate("stale"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotatingAStaleTokenRevokesTheWholeDescendantChain() {
        RefreshToken compromised = withId(activeToken(), 201L);
        ReflectionTestUtils.setField(compromised, "revokedAt", Instant.now().minus(1, ChronoUnit.MINUTES));
        ReflectionTestUtils.setField(compromised, "replacedBy", 202L);

        RefreshToken intermediate = withId(activeToken(), 202L); // already rotated further itself
        ReflectionTestUtils.setField(intermediate, "revokedAt", Instant.now());
        ReflectionTestUtils.setField(intermediate, "replacedBy", 203L);

        RefreshToken currentlyActive = withId(activeToken(), 203L); // the legitimate holder's live token

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(compromised));
        when(refreshTokenRepository.findById(202L)).thenReturn(Optional.of(intermediate));
        when(refreshTokenRepository.findById(203L)).thenReturn(Optional.of(currentlyActive));
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        assertThatThrownBy(() -> refreshTokenService.rotate("compromised"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // The already-revoked intermediate isn't re-saved, only the still-active descendant.
        verify(refreshTokenRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(203L);
        assertThat(captor.getValue().isRevoked()).isTrue();
    }

    @Test
    void rotatingALoggedOutTokenThrowsImmediatelyRegardlessOfGraceWindow() {
        RefreshToken loggedOut = activeToken().revokedBy(null);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(loggedOut));

        assertThatThrownBy(() -> refreshTokenService.rotate("logged-out"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeMarksAnExistingTokenAsRevoked() {
        RefreshToken current = activeToken();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(current));
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        refreshTokenService.revoke("raw-token");

        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isRevoked()).isTrue();
        assertThat(captor.getValue().getReplacedBy()).isNull();
    }

    @Test
    void revokingAnAlreadyRevokedTokenIsANoOp() {
        RefreshToken alreadyRevoked = activeToken().revokedBy(null);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(alreadyRevoked));

        refreshTokenService.revoke("already-revoked");

        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshToken activeToken() {
        return withId(new RefreshToken(1L, "hash", Instant.now().plus(30, ChronoUnit.DAYS)), idSequence.incrementAndGet());
    }

    private void stubSaveReturningSameEntityWithId() {
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> {
            RefreshToken saved = invocation.getArgument(0);
            return saved.getId() != null ? saved : withId(saved, idSequence.incrementAndGet());
        });
    }

    private static <T> T withId(Object entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        @SuppressWarnings("unchecked")
        T typed = (T) entity;
        return typed;
    }
}
