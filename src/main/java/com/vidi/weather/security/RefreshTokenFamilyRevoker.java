package com.vidi.weather.security;

import com.vidi.weather.entity.RefreshToken;
import com.vidi.weather.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a compromised refresh token's whole descendant chain in its own, independent
 * transaction. This has to live outside {@link RefreshTokenService#rotate}: that method throws
 * {@code InvalidRefreshTokenException} right after detecting the theft signal, and since it's
 * {@code @Transactional}, an uncaught exception rolls back everything written during the same
 * transaction -- including the revocations we specifically want to survive the rejection.
 * {@code REQUIRES_NEW} commits this independently before control ever returns to the caller.
 */
@Component
class RefreshTokenFamilyRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    RefreshTokenFamilyRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void revokeFamily(RefreshToken compromised) {
        RefreshToken current = compromised;
        while (current.getReplacedBy() != null) {
            RefreshToken next = refreshTokenRepository.findById(current.getReplacedBy()).orElse(null);
            if (next == null) {
                return;
            }
            if (!next.isRevoked()) {
                refreshTokenRepository.save(next.revokedBy(null));
            }
            current = next;
        }
    }
}
