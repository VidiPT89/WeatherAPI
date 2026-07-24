package com.vidi.weather.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        // Must be constructed after Mockito injects the @Mock fields above, not as an inline
        // field initializer -- instance field initializers run during construction, before the
        // MockitoExtension has a chance to populate @Mock fields, which would capture nulls.
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leavesTheRequestUnauthenticated_whenTheTokensUserNoLongerExists() throws Exception {
        // A previously-issued, still-unexpired JWT for a user deleted after login -- must not
        // throw an uncaught UsernameNotFoundException out of this filter (which runs before
        // DispatcherServlet, so GlobalExceptionHandler never gets a chance to handle it).
        when(request.getHeader("Authorization")).thenReturn("Bearer sometoken");
        when(jwtService.isValid("sometoken")).thenReturn(true);
        when(jwtService.extractEmail("sometoken")).thenReturn("deleted@example.com");
        when(userDetailsService.loadUserByUsername("deleted@example.com"))
                .thenThrow(new UsernameNotFoundException("deleted@example.com"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        org.mockito.Mockito.verify(filterChain).doFilter(request, response);
    }
}
