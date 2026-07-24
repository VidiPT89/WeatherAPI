package com.vidi.weather.security;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the application if {@code jwt.secret} is still the placeholder value checked
 * into {@code application.yml} -- that value is public (readable by anyone with the repo), so
 * running with it would let anyone forge access tokens for any user. The placeholder exists only
 * so local development works without an env var; every real run must override it via
 * {@code JWT_SECRET}.
 */
@Component
class JwtSecretGuard {

    private static final String INSECURE_DEFAULT_SECRET =
            "OCmPQ/BnxKOAKv/4OfYjssQOOCuKOVawTEHC/Cp1v0GIOthc1FlQ6a5hph1Eri+U";

    private final JwtProperties properties;
    private final Environment environment;

    JwtSecretGuard(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void verifySecretIsOverridden() {
        boolean isTestProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (!isTestProfile && INSECURE_DEFAULT_SECRET.equals(properties.secret())) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Refusing to start with the placeholder signing secret "
                            + "committed in application.yml -- it is public, so tokens signed with it can be "
                            + "forged by anyone. Set the JWT_SECRET environment variable before starting.");
        }
    }
}
