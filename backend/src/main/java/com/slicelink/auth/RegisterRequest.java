package com.slicelink.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Registration request")
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72) @Pattern(regexp = ".*[A-Za-z].*", message = "must contain a letter") @Pattern(regexp = ".*\\d.*", message = "must contain a number") String password,
        @NotBlank @Size(min = 1, max = 100) String name) {
}
