package com.vidi.weather.controller;

import com.vidi.weather.dto.UserPreferences;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "User", description = "Authenticated user preferences")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get the authenticated user's preferences")
    public ResponseEntity<UserPreferences> getPreferences(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(toPreferences(principal.getUser()));
    }

    @PostMapping("/preferences")
    @Operation(summary = "Update the authenticated user's preferences")
    public ResponseEntity<UserPreferences> updatePreferences(
            @Valid @RequestBody UserPreferences request, @AuthenticationPrincipal AuthenticatedUser principal) {
        Units units = Units.fromString(request.units());
        User updated = userService.updatePreferredUnits(principal.getUser(), units);
        return ResponseEntity.ok(toPreferences(updated));
    }

    private UserPreferences toPreferences(User user) {
        return new UserPreferences(user.getPreferredUnits().name().toLowerCase());
    }
}
