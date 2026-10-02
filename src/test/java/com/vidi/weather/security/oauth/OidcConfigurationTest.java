package com.vidi.weather.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.vidi.weather.config.OAuthProperties;
import com.vidi.weather.exception.OAuthTokenInvalidException;
import com.vidi.weather.model.OAuthProvider;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Real signature and claim validation against an in-memory key, without a network server. */
class OidcConfigurationTest {
    private RSAKey signingKey;

    @BeforeEach
    void generateKey() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    }

    @SuppressWarnings("unchecked")
    private OidcIdTokenVerifier verifier(List<String> audiences) {
        var config = new OAuthProperties.Provider("https://issuer.example", "https://issuer.example/keys", audiences);
        var verifier = new OidcIdTokenVerifier(new OAuthProperties(config, config, config));
        var processors = (Map<OAuthProvider, DefaultJWTProcessor<SecurityContext>>)
                ReflectionTestUtils.getField(verifier, "processors");
        processors.values().forEach(processor -> processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                JWSAlgorithm.RS256, new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())))));
        return verifier;
    }

    private String token(String audience) throws Exception {
        return token(audience, true);
    }

    private String token(String audience, Object emailVerified) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer("https://issuer.example").subject("user-123")
                .audience(audience).claim("email", "user@example.com").claim("email_verified", emailVerified)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000)).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void rejectsValidSignedTokensWhenNoClientIdsAreConfigured() throws Exception {
        String signedToken = token("unrelated-app");
        for (List<String> audiences : List.of(List.<String>of(), List.of(" "))) {
            assertThatThrownBy(() -> verifier(audiences).verify(OAuthProvider.GOOGLE, signedToken))
                    .isInstanceOf(OAuthTokenInvalidException.class);
        }
    }

    @Test
    void acceptsOnlyTheConfiguredAudience() throws Exception {
        var verifier = verifier(List.of("weather-app"));
        assertThat(verifier.verify(OAuthProvider.GOOGLE, token("weather-app")).subject()).isEqualTo("user-123");
        String unrelatedToken = token("unrelated-app");
        assertThatThrownBy(() -> verifier.verify(OAuthProvider.GOOGLE, unrelatedToken))
                .isInstanceOf(OAuthTokenInvalidException.class);
    }
    @Test
    void absentOrFalseEmailVerificationCannotAuthorizeAccountLinking() throws Exception {
        var verifier = verifier(List.of("weather-app"));
        assertThat(verifier.verify(OAuthProvider.GOOGLE, token("weather-app", null)).emailVerified()).isFalse();
        assertThat(verifier.verify(OAuthProvider.GOOGLE, token("weather-app", false)).emailVerified()).isFalse();
        assertThat(verifier.verify(OAuthProvider.GOOGLE, token("weather-app", "true")).emailVerified()).isTrue();
    }

}
