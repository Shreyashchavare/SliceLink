package com.slicelink.users;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Public user information")
public record UserResponse(Long id, String email, String name, UserStatus status, Instant createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getStatus(), user.getCreatedAt());
    }
}
