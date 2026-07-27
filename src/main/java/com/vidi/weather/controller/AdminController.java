package com.vidi.weather.controller;

import com.vidi.weather.dto.UserResponse;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only user management. Gated by {@code SecurityConfig} ({@code /api/v1/admin/**}
 * requires {@code ROLE_ADMIN}) rather than an in-method check.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin", description = "Admin-only user management")
public class AdminController {

    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "List every registered user")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(adminUserService.listUsers());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user account")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        adminUserService.deleteUser(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
