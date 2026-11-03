package com.township.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final long expiryMinutes;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.expiry-minutes}") long expiryMinutes) {
        this.encoder = encoder;
        this.expiryMinutes = expiryMinutes;
    }

    public long expirySeconds() {
        return expiryMinutes * 60;
    }

    public String create(long userId, String role, Long flatId) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .claim("role", role);
        if (flatId != null) {
            claims.claim("flat_id", flatId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
