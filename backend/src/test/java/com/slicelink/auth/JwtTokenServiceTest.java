package com.slicelink.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slicelink.users.User;
import com.slicelink.users.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties(
                    "test-secret-that-is-at-least-thirty-two-characters-long",
                    Duration.ofMinutes(15),
                    Duration.ofDays(7));

    /** Service using the real system clock — tokens are valid. */
    private final JwtTokenService tokens = new JwtTokenService(PROPERTIES);

    @Test
    void generatesAndValidatesAccessToken() {
        User user = user();
        String token = tokens.issueAccessToken(user).value();
        assertThat(tokens.validate(token, "access").getSubject()).isEqualTo("1");
    }

    @Test
    void accessTokenCarriesEmailClaim() {
        String token = tokens.issueAccessToken(user()).value();
        assertThat(tokens.validate(token, "access").getClaimAsString("email"))
                .isEqualTo("user@example.com");
    }

    @Test
    void rejectsTokenWithWrongType() {
        String refreshToken = tokens.issueRefreshToken(user()).value();
        assertThatThrownBy(() -> tokens.validate(refreshToken, "access"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> tokens.validate("not-a-token", "access"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        // Use a fixed clock set 1 day in the past so the token's issuedAt < expiresAt
        // (satisfying encoder constraints), yet the token is already expired when
        // validated against the current wall clock inside NimbusJwtDecoder.
        Clock pastClock = Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC);
        JwtTokenService pastTokens = new JwtTokenService(PROPERTIES, pastClock);

        String expired = pastTokens.issueAccessToken(user()).value();

        assertThatThrownBy(() -> tokens.validate(expired, "access"))
                .isInstanceOf(JwtException.class);
    }

    // -------------------------------------------------------------------------

    private User user() {
        User user = new User("user@example.com", "hash", "User", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
