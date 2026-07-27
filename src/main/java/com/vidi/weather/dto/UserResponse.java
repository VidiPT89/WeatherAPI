package com.vidi.weather.dto;

import com.vidi.weather.entity.User;
import java.time.Instant;

public record UserResponse(Long id, String email, String role, String units, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name().toLowerCase(),
                user.getPreferredUnits().name().toLowerCase(),
                user.getCreatedAt());
    }
}
