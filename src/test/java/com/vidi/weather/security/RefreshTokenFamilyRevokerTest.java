package com.vidi.weather.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vidi.weather.entity.RefreshToken;
import com.vidi.weather.repository.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenFamilyRevokerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenFamilyRevoker familyRevoker;

    @Test
    void revokesEveryDescendantIncludingTheCurrentlyActiveOne() {
        RefreshToken compromised = tokenWithId(201L);
        ReflectionTestUtils.setField(compromised, "replacedBy", 202L);

        RefreshToken intermediate = tokenWithId(202L); // already revoked, rotated further itself
        ReflectionTestUtils.setField(intermediate, "revokedAt", Instant.now());
        ReflectionTestUtils.setField(intermediate, "replacedBy", 203L);

        RefreshToken currentlyActive = tokenWithId(203L); // the legitimate holder's live token

        when(refreshTokenRepository.findById(202L)).thenReturn(Optional.of(intermediate));
        when(refreshTokenRepository.findById(203L)).thenReturn(Optional.of(currentlyActive));
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        familyRevoker.revokeFamily(compromised);

        // The already-revoked intermediate isn't re-saved, only the still-active descendant.
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(203L);
        assertThat(captor.getValue().isRevoked()).isTrue();
    }

    @Test
    void isANoOpWhenTheCompromisedTokenHasNoDescendants() {
        RefreshToken deadEnd = tokenWithId(301L); // replacedBy is null: e.g. a logged-out token

        familyRevoker.revokeFamily(deadEnd);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void stopsCleanlyIfADescendantIdNoLongerResolves() {
        RefreshToken compromised = tokenWithId(401L);
        ReflectionTestUtils.setField(compromised, "replacedBy", 402L);
        when(refreshTokenRepository.findById(402L)).thenReturn(Optional.empty());

        familyRevoker.revokeFamily(compromised);

        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshToken tokenWithId(long id) {
        RefreshToken token = new RefreshToken(1L, "hash-" + id, Instant.now().plus(30, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(token, "id", id);
        return token;
    }
}
