package com.vidi.weather.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class JwtSecretGuardTest {

    @Mock
    private Environment environment;

    @Test
    void refusesToStart_whenSecretIsStillThePlaceholderOutsideTests() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"default"});
        JwtProperties insecure = new JwtProperties(
                "OCmPQ/BnxKOAKv/4OfYjssQOOCuKOVawTEHC/Cp1v0GIOthc1FlQ6a5hph1Eri+U", 60, 30);
        JwtSecretGuard guard = new JwtSecretGuard(insecure, environment);

        assertThatThrownBy(guard::verifySecretIsOverridden).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsFine_whenSecretHasBeenOverridden() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"default"});
        JwtProperties overridden = new JwtProperties("a-real-deployment-secret", 60, 30);
        JwtSecretGuard guard = new JwtSecretGuard(overridden, environment);

        assertThatCode(guard::verifySecretIsOverridden).doesNotThrowAnyException();
    }

    @Test
    void startsFine_withThePlaceholderSecret_underTheTestProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        JwtProperties insecure = new JwtProperties(
                "OCmPQ/BnxKOAKv/4OfYjssQOOCuKOVawTEHC/Cp1v0GIOthc1FlQ6a5hph1Eri+U", 60, 30);
        JwtSecretGuard guard = new JwtSecretGuard(insecure, environment);

        assertThatCode(guard::verifySecretIsOverridden).doesNotThrowAnyException();
    }
}
