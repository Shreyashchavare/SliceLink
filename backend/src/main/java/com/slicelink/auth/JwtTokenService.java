package com.slicelink.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.slicelink.users.User;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class JwtTokenService {

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    /** Production constructor — uses the system UTC clock. */
    @Autowired
    public JwtTokenService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Full constructor — allows test clock injection. */
    JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public IssuedToken issueAccessToken(User user) {
        return issue(user, "access", properties.accessTokenTtl());
    }

    public IssuedToken issueRefreshToken(User user) {
        return issue(user, "refresh", properties.refreshTokenTtl());
    }

    public Jwt validate(String token, String expectedType) {
        Jwt jwt = decoder.decode(token);
        if (!expectedType.equals(jwt.getClaimAsString("token_type"))) {
            throw new org.springframework.security.oauth2.jwt.JwtValidationException(
                    "Unexpected token type",
                    java.util.List.of(new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token", "Unexpected token type", null)));
        }
        return jwt;
    }

    private IssuedToken issue(User user, String type, Duration ttl) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(ttl);
        return issue(user, type, issuedAt, expiresAt);
    }

    private IssuedToken issue(User user, String type, Instant issuedAt, Instant expiresAt) {
        String tokenId = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(tokenId)
                .claim("token_type", type)
                .claim("email", user.getEmail())
                .build();
        String token = encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims))
                .getTokenValue();
        return new IssuedToken(token, tokenId, expiresAt);
    }

    public record IssuedToken(String value, String tokenId, Instant expiresAt) { }
    public long remainingLifetimeSeconds(Instant expiresAt) {
    return Math.max(
            0L,
            Duration.between(
                    Instant.now(clock),
                    expiresAt
            ).getSeconds()
    );
}
}
