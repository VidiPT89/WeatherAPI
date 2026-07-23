package com.vidi.weather.security;

import com.vidi.weather.entity.RefreshToken;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.InvalidRefreshTokenException;
import com.vidi.weather.repository.RefreshTokenRepository;
import com.vidi.weather.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, rotating refresh tokens. Stored server-side only as a SHA-256 hash (fast hashing is
 * fine here: the raw value is a 256-bit SecureRandom secret, not a low-entropy human password,
 * so there is nothing to protect against offline guessing).
 */
@Service
public class RefreshTokenService {

    /**
     * A just-revoked token is still accepted for this long. Without it, two legitimate concurrent
     * refresh attempts against the same stored token (e.g. two browser tabs, or a proactive and a
     * reactive refresh racing on the web client) would spuriously fail one side instead of both
     * succeeding. Reuse past this window is treated as a stale/possibly-stolen token.
     */
    private static final Duration REUSE_GRACE_WINDOW = Duration.ofSeconds(10);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final long expirationDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository, UserRepository userRepository, JwtProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.expirationDays = properties.refreshExpirationDays();
    }

    public record RotationResult(User user, String refreshToken) {
    }

    public String issue(User user) {
        String raw = generateRawToken();
        refreshTokenRepository.save(new RefreshToken(user.getId(), hash(raw), newExpiry()));
        return raw;
    }

    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isExpired()) {
            throw new InvalidRefreshTokenException();
        }
        if (current.isRevoked() && !isWithinRotationGraceWindow(current)) {
            // Either logged-out (replacedBy == null -- revokeFamily is then a no-op, nothing to
            // walk) or a rotation reuse attempt past the grace window: reusing a token this stale
            // means whoever is presenting it either has a very out-of-date copy or stole it at
            // some point in the chain. Revoke every descendant too, forcing the legitimate holder
            // of the current token to also re-authenticate rather than leaving it usable.
            revokeFamily(current);
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);

        String newRaw = generateRawToken();
        RefreshToken next = refreshTokenRepository.save(
                new RefreshToken(user.getId(), hash(newRaw), newExpiry()));
        if (!current.isRevoked()) {
            refreshTokenRepository.save(current.revokedBy(next.getId()));
        }

        return new RotationResult(user, newRaw);
    }

    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> refreshTokenRepository.save(token.revokedBy(null)));
    }

    /** Walks the {@code replacedBy} chain forward from a compromised token, revoking every
     * descendant -- including whichever one is currently active -- so the whole lineage needs
     * a fresh login. */
    private void revokeFamily(RefreshToken compromised) {
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

    private boolean isWithinRotationGraceWindow(RefreshToken token) {
        return token.getReplacedBy() != null
                && token.getRevokedAt().isAfter(Instant.now().minus(REUSE_GRACE_WINDOW));
    }

    private Instant newExpiry() {
        return Instant.now().plus(expirationDays, ChronoUnit.DAYS);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        try {
            SecureRandom.getInstanceStrong().nextBytes(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No strong SecureRandom algorithm available", e);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
