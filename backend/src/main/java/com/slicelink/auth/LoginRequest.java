package com.slicelink.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Login request")
public record LoginRequest(@NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 72) String password) {
}
