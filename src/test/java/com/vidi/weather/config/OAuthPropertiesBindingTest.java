package com.vidi.weather.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

/**
 * Binds the real application.yml -- the iOS bundle ID must stay an accepted Apple audience no
 * matter what APPLE_OAUTH_CLIENT_IDS holds on the deployment (App Review rejected 1.0 (2) because
 * the variable was missing there and every Sign in with Apple token was refused).
 */
class OAuthPropertiesBindingTest {

    private static final String IOS_BUNDLE_ID = "dev.ividi.weatherapp";

    private List<String> appleAudiences(Map<String, Object> env) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("env", env));
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));
        List<String> clientIds = binder.bind("oauth", OAuthProperties.class).get().apple().clientIds();
        return clientIds.stream().filter(id -> !id.isBlank()).toList();
    }

    @Test
    void acceptsTheIosBundleIdWhenTheEnvVarIsUnset() throws IOException {
        assertThat(appleAudiences(Map.of())).containsExactly(IOS_BUNDLE_ID);
    }

    @Test
    void keepsTheIosBundleIdWhenTheEnvVarAddsOtherAudiences() throws IOException {
        assertThat(appleAudiences(Map.of("APPLE_OAUTH_CLIENT_IDS", "dev.ividi.weatherapp.web")))
                .containsExactly(IOS_BUNDLE_ID, "dev.ividi.weatherapp.web");
    }
}
