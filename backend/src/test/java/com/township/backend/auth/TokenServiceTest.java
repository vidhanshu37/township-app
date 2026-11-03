package com.township.backend.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private final SecretKey key = new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256).build();
    private final TokenService service =
            new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), 60);

    @Test
    void tokenCarriesUserRoleAndFlat() {
        Jwt jwt = decoder.decode(service.create(7L, "RESIDENT", 3L));
        assertEquals("7", jwt.getSubject());
        assertEquals("RESIDENT", jwt.getClaimAsString("role"));
        assertEquals(3L, ((Number) jwt.getClaim("flat_id")).longValue());
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void staffTokenHasNoFlat() {
        Jwt jwt = decoder.decode(service.create(9L, "HELP_DESK", null));
        assertNull(jwt.getClaim("flat_id"));
    }

    @Test
    void tamperedRoleIsRejected() {
        String[] parts = service.create(7L, "RESIDENT", 3L).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("RESIDENT", "COMMITTEE");
        String forged = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];
        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }
}