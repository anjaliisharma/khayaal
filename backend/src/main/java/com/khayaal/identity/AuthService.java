package com.khayaal.identity;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;
    private final AccessTokenService accessTokens;
    private final Clock clock;
    private final Duration refreshLifetime;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcClient jdbc, PasswordEncoder passwords, AccessTokenService accessTokens, Clock clock,
                       SecurityProperties properties) {
        this.jdbc = jdbc; this.passwords = passwords; this.accessTokens = accessTokens; this.clock = clock;
        this.refreshLifetime = Duration.ofDays(properties.refreshTokenDays());
    }

    @Transactional
    public AuthResult register(String email, String password, String displayName) {
        String normalized = normalizeEmail(email);
        if (jdbc.sql("select count(*) from user_accounts where email = :email").param("email", normalized).query(Long.class).single() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        UUID userId = UUID.randomUUID();
        jdbc.sql("insert into user_accounts(id,email,password_hash,display_name,created_at) values (:id,:email,:password,:name,:now)")
                .param("id", userId).param("email", normalized).param("password", passwords.encode(password))
                .param("name", displayName.trim()).param("now", Timestamp.from(clock.instant())).update();
        return createSession(userId, normalized);
    }

    @Transactional
    public AuthResult login(String email, String password) {
        UserRow user = jdbc.sql("select id,email,password_hash from user_accounts where email=:email")
                .param("email", normalizeEmail(email)).query((rs, row) -> new UserRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")))
                .optional().orElseThrow(() -> unauthorized("Invalid credentials"));
        if (!passwords.matches(password, user.passwordHash())) throw unauthorized("Invalid credentials");
        return createSession(user.id(), user.email());
    }

    @Transactional
    public AuthResult refresh(String rawToken) {
        Instant now = clock.instant();
        UserRow user = jdbc.sql("select u.id,u.email,u.password_hash from refresh_tokens r join user_accounts u on u.id=r.user_id " +
                        "where r.token_hash=:hash and r.revoked_at is null and r.expires_at>:now for update")
                .param("hash", hash(rawToken)).param("now", Timestamp.from(now))
                .query((rs, row) -> new UserRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")))
                .optional().orElseThrow(() -> unauthorized("Refresh token is invalid or expired"));
        revoke(rawToken);
        return createSession(user.id(), user.email());
    }

    public void revoke(String rawToken) {
        if (rawToken != null) jdbc.sql("update refresh_tokens set revoked_at=:now where token_hash=:hash and revoked_at is null")
                .param("now", Timestamp.from(clock.instant())).param("hash", hash(rawToken)).update();
    }

    private AuthResult createSession(UUID userId, String email) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.sql("insert into refresh_tokens(id,user_id,token_hash,expires_at,created_at) values (:id,:user,:hash,:expires,:now)")
                .param("id", UUID.randomUUID()).param("user", userId).param("hash", hash(refreshToken))
                .param("expires", Timestamp.from(now.plus(refreshLifetime))).param("now", Timestamp.from(now)).update();
        return new AuthResult(accessTokens.issue(userId, email), refreshToken, refreshLifetime);
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private ResponseStatusException unauthorized(String message) { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message); }
    private String hash(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private record UserRow(UUID id, String email, String passwordHash) { }
    public record AuthResult(String accessToken, String refreshToken, Duration refreshLifetime) { }
}
