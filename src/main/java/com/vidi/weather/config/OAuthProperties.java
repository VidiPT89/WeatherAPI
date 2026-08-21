package com.vidi.weather.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One {@link Provider} entry per social login provider -- {@code clientIds} is the (non-secret)
 * audience an ID token must be issued for; no client secret is needed anywhere here, since the
 * backend only verifies tokens the client already obtained natively, it never performs the
 * authorization-code exchange itself.
 */
@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Provider google, Provider apple, Provider microsoft) {

    public record Provider(String issuer, String jwksUri, List<String> clientIds) {
    }
}
