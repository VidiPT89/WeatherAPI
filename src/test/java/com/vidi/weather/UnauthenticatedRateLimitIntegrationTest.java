package com.vidi.weather;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vidi.weather.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the gap {@link RateLimitFilter} used to have: a request that reaches the filter without
 * an authenticated principal -- no {@code Authorization} header at all, or a malformed/garbage
 * one that {@code JwtAuthenticationFilter} couldn't turn into an {@link
 * com.vidi.weather.security.AuthenticatedUser} -- previously matched neither the authenticated
 * branch nor the {@code /api/v1/auth/**} branch, so it skipped rate limiting entirely until Spring
 * Security rejected it with 401 further down the chain.
 *
 * <p>Isolated from {@link AuthAndSecurityIntegrationTest} and {@link
 * AuthRateLimitIntegrationTest} on purpose, for the same reason those two are isolated from each
 * other: this overrides {@code rate-limit.unauthenticated-requests-per-minute} to a low value for
 * the whole test class, and Spring caches the application context per distinct property set --
 * sharing a class with tests that hit protected endpoints many times (keyed by the same simulated
 * client IP) would make this bucket bleed across unrelated tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "rate-limit.unauthenticated-requests-per-minute=3")
class UnauthenticatedRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestsWithNoTokenAtAllAreRateLimitedByIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/weather/history"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/v1/weather/history"))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Keyed on a distinct {@code X-Forwarded-For} hop from the no-token test above so the two
     * don't share a bucket and bleed into each other within this class's single cached context.
     */
    @Test
    void aGarbageBearerTokenIsRateLimitedTheSameAsNoTokenAtAll() throws Exception {
        String forwardedFor = "198.51.100.23";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/weather/history")
                            .header("X-Forwarded-For", forwardedFor)
                            .header("Authorization", "Bearer not-a-real-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/v1/weather/history")
                        .header("X-Forwarded-For", forwardedFor)
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isTooManyRequests());
    }
}
