package com.vidi.weather.controller;

import com.vidi.weather.dto.AuthResponse;
import com.vidi.weather.dto.LoginRequest;
import com.vidi.weather.dto.RefreshRequest;
import com.vidi.weather.dto.RegisterRequest;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.InvalidCredentialsException;
import com.vidi.weather.security.JwtService;
import com.vidi.weather.security.RefreshTokenService;
import com.vidi.weather.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration and login")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.email(), request.password());
        AuthResponse response = authResponseFor(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }

        User user = userService.findByEmail(request.email());
        return ResponseEntity.ok(authResponseFor(user));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access + refresh token pair")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.RotationResult result = rotateWithRetry(request.refreshToken());
        AuthResponse response = AuthResponse.bearer(
                jwtService.generateToken(result.user()), jwtService.expirationSeconds(), result.refreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Two truly concurrent rotations of the same refresh token (a proactive and a reactive
     * refresh racing on a client) both start their transaction before either commits; whichever
     * commits second loses the optimistic-lock check on {@code RefreshToken.version} instead of
     * silently overwriting the winner's write. Retrying re-reads the (now-revoked) token fresh
     * and returns the winner's cached rotation via {@link RefreshTokenService}'s grace-window
     * path -- so both callers end up with the same new pair rather than one getting an orphaned,
     * untracked token. The retry is a brand-new call through the Spring proxy (a fresh
     * transaction/EntityManager), not a loop inside the already-failed one.
     */
    private RefreshTokenService.RotationResult rotateWithRetry(String rawToken) {
        try {
            return refreshTokenService.rotate(rawToken);
        } catch (OptimisticLockingFailureException raceLost) {
            return refreshTokenService.rotate(rawToken);
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse authResponseFor(User user) {
        String refreshToken = refreshTokenService.issue(user);
        return AuthResponse.bearer(jwtService.generateToken(user), jwtService.expirationSeconds(), refreshToken);
    }
}
