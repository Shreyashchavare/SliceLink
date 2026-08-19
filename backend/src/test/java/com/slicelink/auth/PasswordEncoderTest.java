package com.slicelink.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordEncoderTest {
    @Test
    void hashesPasswordsWithoutPersistingPlaintext() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("SecurePassword123");
        assertThat(hash).isNotEqualTo("SecurePassword123");
        assertThat(encoder.matches("SecurePassword123", hash)).isTrue();
        assertThat(encoder.matches("WrongPassword123", hash)).isFalse();
    }
}
