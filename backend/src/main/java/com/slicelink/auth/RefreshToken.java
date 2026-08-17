package com.slicelink.auth;

import com.slicelink.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "token_id", nullable = false, length = 64) private String tokenId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RefreshToken() { }
    public RefreshToken(User user, String tokenHash, String tokenId, Instant expiresAt) {
        this.user = user; this.tokenHash = tokenHash; this.tokenId = tokenId; this.expiresAt = expiresAt; this.createdAt = Instant.now();
    }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public String getTokenId() { return tokenId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isActiveAt(Instant instant) { return revokedAt == null && expiresAt.isAfter(instant); }
    public void revoke() { if (revokedAt == null) { revokedAt = Instant.now(); } }
}
