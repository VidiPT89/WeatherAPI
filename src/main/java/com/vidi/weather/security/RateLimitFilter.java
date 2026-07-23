package com.vidi.weather.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vidi.weather.dto.ErrorResponse;
import com.vidi.weather.exception.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final Cache<String, Bucket> bucketsByUser;
    private final Cache<String, Bucket> bucketsByIp;
    private final int requestsPerMinute;
    private final int authRequestsPerMinute;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(int requestsPerMinute, int authRequestsPerMinute, ObjectMapper objectMapper) {
        this.requestsPerMinute = requestsPerMinute;
        this.authRequestsPerMinute = authRequestsPerMinute;
        this.objectMapper = objectMapper;
        this.bucketsByUser = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();
        this.bucketsByIp = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            Bucket bucket = bucketsByUser.get(authenticatedUser.getUsername(), key -> newBucket(requestsPerMinute));
            consumeOrReject(bucket, request, response, filterChain);
            return;
        }

        // Login/register/refresh/logout have no authenticated principal to key a bucket on,
        // but they're exactly what credential-stuffing/brute-force attacks target -- rate-limit
        // them by client IP instead.
        if (request.getRequestURI().startsWith(AUTH_PATH_PREFIX)) {
            Bucket bucket = bucketsByIp.get(resolveClientIp(request), key -> newBucket(authRequestsPerMinute));
            consumeOrReject(bucket, request, response, filterChain);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void consumeOrReject(
            Bucket bucket, HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            writeRateLimitExceeded(request, response);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newBucket(int limitPerMinute) {
        Bandwidth limit = Bandwidth.classic(limitPerMinute, Refill.greedy(limitPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private void writeRateLimitExceeded(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded. Please slow down and try again shortly.",
                request.getRequestURI(),
                ErrorCode.RATE_LIMIT_EXCEEDED);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
