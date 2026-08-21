package com.vidi.weather.security.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vidi.weather.config.OAuthProperties;
import com.vidi.weather.exception.OAuthTokenInvalidException;
import com.vidi.weather.model.OAuthProvider;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OidcIdTokenVerifierTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private static final String ISSUER = "https://accounts.google.com";
    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";

    private RSAKey signingKey;
    private OidcIdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
        wireMock.stubFor(get(urlPathEqualTo("/certs"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[" + signingKey.toPublicJWK().toJSONObject() + "]}")));

        OAuthProperties.Provider google = new OAuthProperties.Provider(
                ISSUER, wireMock.baseUrl() + "/certs", List.of(CLIENT_ID));
        // Apple/Microsoft point at the same stub -- only GOOGLE is exercised by these tests.
        OAuthProperties properties = new OAuthProperties(google, google, google);
        verifier = new OidcIdTokenVerifier(properties);
    }

    private String signedToken(JWTClaimsSet.Builder claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims.build());
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder validClaims() {
        Date now = new Date();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("user-subject-123")
                .audience(CLIENT_ID)
                .claim("email", "someone@example.com")
                .claim("email_verified", true)
                .issueTime(now)
                .expirationTime(new Date(now.getTime() + 60_000));
    }

    @Test
    void verifiesAWellFormedTokenSignedByTheRegisteredKey() throws JOSEException {
        String token = signedToken(validClaims());

        OidcIdTokenVerifier.VerifiedIdentity identity = verifier.verify(OAuthProvider.GOOGLE, token);

        assertThat(identity.email()).isEqualTo("someone@example.com");
        assertThat(identity.subject()).isEqualTo("user-subject-123");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void treatsAMissingEmailVerifiedClaimAsVerified() throws JOSEException {
        // Apple omits email_verified entirely for accounts it already verified at sign-up.
        Date now = new Date();
        JWTClaimsSet.Builder claimsWithoutFlag = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("user-subject-123")
                .audience(CLIENT_ID)
                .claim("email", "someone@example.com")
                .issueTime(now)
                .expirationTime(new Date(now.getTime() + 60_000));
        String token = signedToken(claimsWithoutFlag);

        OidcIdTokenVerifier.VerifiedIdentity identity = verifier.verify(OAuthProvider.GOOGLE, token);

        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void rejectsAnUnverifiedEmail() throws JOSEException {
        String token = signedToken(validClaims().claim("email_verified", false));

        OidcIdTokenVerifier.VerifiedIdentity identity = verifier.verify(OAuthProvider.GOOGLE, token);

        assertThat(identity.emailVerified()).isFalse();
    }

    @Test
    void rejectsATokenWithTheWrongAudience() throws JOSEException {
        String token = signedToken(
                new JWTClaimsSet.Builder(validClaims().build()).audience("some-other-client-id"));

        assertThatThrownBy(() -> verifier.verify(OAuthProvider.GOOGLE, token))
                .isInstanceOf(OAuthTokenInvalidException.class);
    }

    @Test
    void rejectsATokenFromTheWrongIssuer() throws JOSEException {
        String token = signedToken(
                new JWTClaimsSet.Builder(validClaims().build()).issuer("https://not-google.example"));

        assertThatThrownBy(() -> verifier.verify(OAuthProvider.GOOGLE, token))
                .isInstanceOf(OAuthTokenInvalidException.class);
    }

    @Test
    void rejectsAnExpiredToken() throws JOSEException {
        Date past = new Date(System.currentTimeMillis() - 120_000);
        String token = signedToken(new JWTClaimsSet.Builder(validClaims().build())
                .expirationTime(past));

        assertThatThrownBy(() -> verifier.verify(OAuthProvider.GOOGLE, token))
                .isInstanceOf(OAuthTokenInvalidException.class);
    }

    @Test
    void rejectsATokenSignedByAnUnregisteredKey() throws JOSEException {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("other-key").generate();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(otherKey.getKeyID()).build(),
                validClaims().build());
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(otherKey));

        assertThatThrownBy(() -> verifier.verify(OAuthProvider.GOOGLE, jwt.serialize()))
                .isInstanceOf(OAuthTokenInvalidException.class);
    }
}
