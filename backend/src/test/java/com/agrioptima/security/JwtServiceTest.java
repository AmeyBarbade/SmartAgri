package com.agrioptima.security;

import com.agrioptima.entity.Role;
import com.agrioptima.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("unit-test-secret-key-that-is-long-enough-for-hs256".getBytes());
    private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");

    private static JwtService serviceAt(Instant now, String secret) {
        return new JwtService(new JwtProperties(secret, 60), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User user(long id) {
        User u = new User("Test", "t@example.com", "hash", Role.FARMER);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void issuedTokenVerifiesToTheUserId() {
        JwtService jwt = serviceAt(T0, SECRET);
        JwtService.IssuedToken issued = jwt.issue(user(42));

        assertThat(issued.expiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(60)));
        assertThat(jwt.verify(issued.token())).contains(42L);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(T0, SECRET).issue(user(1)).token();

        assertThat(serviceAt(T0.plus(Duration.ofMinutes(59)), SECRET).verify(token)).contains(1L);
        assertThat(serviceAt(T0.plus(Duration.ofMinutes(61)), SECRET).verify(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String otherSecret = Base64.getEncoder()
                .encodeToString("a-completely-different-secret-key-for-signing-tokens".getBytes());
        String forged = serviceAt(T0, otherSecret).issue(user(1)).token();

        assertThat(serviceAt(T0, SECRET).verify(forged)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = serviceAt(T0, SECRET);
        String[] parts = jwt.issue(user(1)).token().split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"agrioptima\",\"sub\":\"2\",\"exp\":9999999999}".getBytes());

        assertThat(jwt.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void unsignedAndGarbageTokensAreRejected() {
        JwtService jwt = serviceAt(T0, SECRET);
        String[] parts = jwt.issue(user(1)).token().split("\\.");
        String algNone = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());

        assertThat(jwt.verify(algNone + "." + parts[1] + ".")).isEmpty();
        assertThat(jwt.verify("not-a-jwt")).isEmpty();
        assertThat(jwt.verify("")).isEmpty();
    }

    @Test
    void weakOrMissingSecretFailsFast() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> serviceAt(T0, shortSecret)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> serviceAt(T0, "")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> serviceAt(T0, null)).isInstanceOf(IllegalStateException.class);
    }
}
