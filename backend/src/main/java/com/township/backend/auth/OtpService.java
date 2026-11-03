package com.township.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RNG = new SecureRandom();

    private final JdbcClient jdbc;
    private final SecretKey key;

    public OtpService(JdbcClient jdbc, SecretKey jwtSecretKey) {
        this.jdbc = jdbc;
        this.key = jwtSecretKey;
    }

    private record Challenge(long id, String hash, int attempts) {}

    @Transactional
    public String issue(String phone) {
        int recent = jdbc.sql("""
                SELECT count(*) FROM otp_challenge
                WHERE phone = :p AND created_at > now() - interval '30 seconds'""")
                .param("p", phone).query(Integer.class).single();
        if (recent > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before requesting another OTP");
        }
        String otp = String.format("%06d", RNG.nextInt(1_000_000));
        jdbc.sql("""
                INSERT INTO otp_challenge (phone, otp_hash, expires_at)
                VALUES (:p, :h, now() + interval '5 minutes')""")
                .param("p", phone).param("h", hash(phone, otp)).update();
        return otp;
    }

    @Transactional
    public boolean verify(String phone, String otp) {
        var challenge = jdbc.sql("""
                SELECT id, otp_hash, attempts FROM otp_challenge
                WHERE phone = :p AND consumed = false AND expires_at > now()
                ORDER BY created_at DESC LIMIT 1 FOR UPDATE""")
                .param("p", phone)
                .query((rs, n) -> new Challenge(rs.getLong("id"), rs.getString("otp_hash"), rs.getInt("attempts")))
                .optional();
        if (challenge.isEmpty() || challenge.get().attempts() >= MAX_ATTEMPTS) {
            return false;
        }
        Challenge c = challenge.get();
        jdbc.sql("UPDATE otp_challenge SET attempts = attempts + 1 WHERE id = :id")
                .param("id", c.id()).update();
        boolean ok = MessageDigest.isEqual(
                c.hash().getBytes(StandardCharsets.UTF_8),
                hash(phone, otp).getBytes(StandardCharsets.UTF_8));
        if (ok) {
            jdbc.sql("UPDATE otp_challenge SET consumed = true WHERE id = :id")
                    .param("id", c.id()).update();
        }
        return ok;
    }

    private String hash(String phone, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(
                    mac.doFinal((phone + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}