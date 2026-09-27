package com.agrioptima.dto.auth;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

    public static AuthResponse bearer(String token, Instant expiresAt, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresAt, user);
    }
}
