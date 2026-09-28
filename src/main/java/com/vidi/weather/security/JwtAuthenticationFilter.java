package com.vidi.weather.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Set when the caller's user couldn't be loaded because the database is unreachable, so
     * {@link RestAuthenticationEntryPoint} answers a protected endpoint with 503 instead of 401 --
     * a 401 would make the clients discard a perfectly valid session.
     */
    public static final String DATABASE_UNAVAILABLE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".databaseUnavailable";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            authenticate(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        if (!jwtService.isValid(token) || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        String email = jwtService.extractEmail(token);
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException userDeletedSinceTokenWasIssued) {
            // A previously-issued, still-unexpired JWT for a user who no longer exists (deleted
            // after login). This filter runs before DispatcherServlet, so GlobalExceptionHandler
            // never sees an exception thrown from here -- leaving the security context empty is
            // what turns this into the normal 401 (via RestAuthenticationEntryPoint) instead of a
            // container-level 500.
            return;
        } catch (DataAccessException | TransactionException databaseUnavailable) {
            // Same reasoning as above, but for a database outage: carry on unauthenticated so the
            // anonymous weather endpoints still answer, and flag it for the entry point.
            log.warn("Could not load the authenticated user, database unavailable: {}", databaseUnavailable.getMessage());
            request.setAttribute(DATABASE_UNAVAILABLE_ATTRIBUTE, Boolean.TRUE);
            return;
        }

        var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
