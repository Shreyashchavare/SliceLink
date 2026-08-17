package com.slicelink.auth;

import com.slicelink.shared.ApiException;
import com.slicelink.users.User;
import com.slicelink.users.UserRepository;
import com.slicelink.users.UserResponse;
import com.slicelink.users.UserStatus;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final TokenHashService tokenHashService;

    public AuthenticationService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                                 PasswordEncoder passwordEncoder, JwtTokenService tokenService, TokenHashService tokenHashService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.tokenHashService = tokenHashService;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()), request.name().trim(), UserStatus.ACTIVE));
        return authenticate(user);
    }

    @Transactional
    public AuthenticationResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()) || user.getStatus() != UserStatus.ACTIVE) {
            throw invalidCredentials();
        }
        return authenticate(user);
    }

    @Transactional
    public AuthenticationResponse refresh(RefreshTokenRequest request) {
        Jwt jwt = validateRefreshToken(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenId(jwt.getId()).orElseThrow(this::invalidRefreshToken);
        if (!stored.isActiveAt(Instant.now()) || !stored.getTokenHash().equals(tokenHashService.hash(request.refreshToken()))
                || !stored.getUser().getId().toString().equals(jwt.getSubject()) || stored.getUser().getStatus() != UserStatus.ACTIVE) {
            throw invalidRefreshToken();
        }
        stored.revoke();
        return authenticate(stored.getUser());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        Jwt jwt = validateRefreshToken(request.refreshToken());
        refreshTokenRepository.findByTokenId(jwt.getId())
                .filter(token -> token.getTokenHash().equals(tokenHashService.hash(request.refreshToken())))
                .ifPresent(RefreshToken::revoke);
    }

    private AuthenticationResponse authenticate(User user) {

    JwtTokenService.IssuedToken access =
            tokenService.issueAccessToken(user);

    JwtTokenService.IssuedToken refresh =
            tokenService.issueRefreshToken(user);

    refreshTokenRepository.save(
            new RefreshToken(
                    user,
                    tokenHashService.hash(refresh.value()),
                    refresh.tokenId(),
                    refresh.expiresAt()
            )
    );

    long expiresIn =
            tokenService.remainingLifetimeSeconds(
                    access.expiresAt()
            );

    return new AuthenticationResponse(
            access.value(),
            refresh.value(),
            "Bearer",
            expiresIn,
            UserResponse.from(user)
    );
}

    private Jwt validateRefreshToken(String token) {
        try { return tokenService.validate(token, "refresh"); }
        catch (JwtException | IllegalArgumentException exception) { throw invalidRefreshToken(); }
    }
    private ApiException invalidCredentials() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS); }
    private ApiException invalidRefreshToken() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired."); }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
