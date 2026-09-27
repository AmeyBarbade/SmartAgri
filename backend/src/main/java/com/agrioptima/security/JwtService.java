package com.agrioptima.security;

import com.agrioptima.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies HMAC-signed JWTs. Subject = user id. */
@Service
public class JwtService {

    static final String ISSUER = "agrioptima";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be set");
        }
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 256 bits");
        }
        if (properties.expirationMinutes() <= 0) {
            throw new IllegalStateException("app.jwt.expiration-minutes must be positive");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.ttl = Duration.ofMinutes(properties.expirationMinutes());
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** @return the user id if the token is authentic, unexpired and issued by us; empty otherwise. */
    public Optional<Long> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
