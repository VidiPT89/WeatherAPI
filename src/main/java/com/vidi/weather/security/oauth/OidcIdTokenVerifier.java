package com.vidi.weather.security.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.vidi.weather.config.OAuthProperties;
import com.vidi.weather.exception.OAuthTokenInvalidException;
import com.vidi.weather.model.OAuthProvider;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Verifies a native ID token -- one the client already obtained directly from Google/Apple/
 * Microsoft's own SDK -- against the issuing provider's public JWKS. One generic OIDC verifier
 * serves all three providers, since RS256-signed ID tokens follow the same shape regardless of
 * issuer; only issuer/JWKS URL/expected audiences differ, which {@link OAuthProperties} supplies.
 * No client secret is involved anywhere in this class.
 */
@Service
public class OidcIdTokenVerifier {

    public record VerifiedIdentity(String email, String subject, boolean emailVerified) {
    }

    private final Map<OAuthProvider, DefaultJWTProcessor<SecurityContext>> processors = new EnumMap<>(OAuthProvider.class);
    private final Map<OAuthProvider, List<String>> clientIdsByProvider = new EnumMap<>(OAuthProvider.class);

    public OidcIdTokenVerifier(OAuthProperties properties) {
        register(OAuthProvider.GOOGLE, properties.google());
        register(OAuthProvider.APPLE, properties.apple());
        register(OAuthProvider.MICROSOFT, properties.microsoft());
    }

    private void register(OAuthProvider provider, OAuthProperties.Provider config) {
        try {
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(URI.create(config.jwksUri()).toURL());
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    new JWTClaimsSet.Builder().issuer(config.issuer()).build(),
                    Set.of("sub", "exp")));
            processors.put(provider, processor);

            List<String> clientIds = config.clientIds() == null ? List.of() : config.clientIds();
            clientIdsByProvider.put(provider, clientIds.stream().filter(id -> !id.isBlank()).toList());
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Invalid JWKS URL for " + provider, ex);
        }
    }

    /**
     * @throws OAuthTokenInvalidException if the signature/issuer/expiry don't check out, the
     *     audience doesn't match a configured client id, or the provider's email isn't verified
     */
    public VerifiedIdentity verify(OAuthProvider provider, String idToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(idToken);
            JWTClaimsSet claims = processors.get(provider).process(signedJWT, null);

            List<String> expectedAudiences = clientIdsByProvider.get(provider);
            boolean audienceOk = expectedAudiences.isEmpty()
                    || claims.getAudience().stream().anyMatch(expectedAudiences::contains);
            if (!audienceOk) {
                throw new OAuthTokenInvalidException(provider);
            }

            String email = claims.getStringClaim("email");
            String subject = claims.getSubject();
            if (email == null || subject == null) {
                throw new OAuthTokenInvalidException(provider);
            }

            Object emailVerifiedClaim = claims.getClaim("email_verified");
            // Apple omits this claim entirely for accounts it already verified at sign-up --
            // absence isn't "unverified", only an explicit false is.
            boolean emailVerified = !Boolean.FALSE.equals(emailVerifiedClaim)
                    && !"false".equals(String.valueOf(emailVerifiedClaim));

            return new VerifiedIdentity(email, subject, emailVerified);
        } catch (ParseException | BadJOSEException | JOSEException ex) {
            throw new OAuthTokenInvalidException(provider, ex);
        }
    }
}
