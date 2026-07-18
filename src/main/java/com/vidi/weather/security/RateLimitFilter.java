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

    private final Cache<String, Bucket> bucketsByUser;
    private final int requestsPerMinute;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(int requestsPerMinute, ObjectMapper objectMapper) {
        this.requestsPerMinute = requestsPerMinute;
        this.objectMapper = objectMapper;
        this.bucketsByUser = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = bucketsByUser.get(authenticatedUser.getUsername(), key -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            writeRateLimitExceeded(request, response);
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));
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
