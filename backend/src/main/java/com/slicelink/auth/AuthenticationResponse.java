package com.slicelink.auth;

import com.slicelink.users.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication tokens and public user information")
public record AuthenticationResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserResponse user) {
}