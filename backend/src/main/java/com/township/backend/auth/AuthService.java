package com.township.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.Optional;

@Service
public class AuthService {
    public record UserRow(long id, String name, String role, Long flatId) {}
    public record LoginResult(String accessToken, long expiresInSeconds, String role, String name) {}

    private final JdbcClient jdbc;
    private final OtpService otp;
    private final TokenService tokens;
    private final boolean demoMode;

    public AuthService(JdbcClient jdbc, OtpService otp, TokenService tokens,
                       @Value("${app.demo-mode:false}") boolean demoMode) {
        this.jdbc = jdbc;
        this.otp = otp;
        this.tokens = tokens;
        this.demoMode = demoMode;
    }

    public Optional<String> requestOtp(String phone) {
        Optional<UserRow> user = findByPhone(phone);
        if (user.isEmpty()) {
            audit(null, "OTP_REQUEST_UNKNOWN_PHONE", phone);
            return Optional.empty();
        }
        String code = otp.issue(phone);
        audit(user.get().id(), "OTP_REQUESTED", phone);
        return demoMode ? Optional.of(code) : Optional.empty();
    }

    public Optional<LoginResult> verify(String phone, String code) {
        Optional<UserRow> user = findByPhone(phone);
        if (user.isEmpty() || !otp.verify(phone, code)) {
            audit(user.map(UserRow::id).orElse(null), "LOGIN_FAILED", phone);
            return Optional.empty();
        }
        UserRow u = user.get();
        audit(u.id(), "LOGIN_SUCCESS", phone);
        String token = tokens.create(u.id(), u.role(), u.flatId());
        return Optional.of(new LoginResult(token, tokens.expirySeconds(), u.role(), u.name()));
    }

    private Optional<UserRow> findByPhone(String phone) {
        return jdbc.sql("SELECT id, name, role, flat_id FROM users WHERE phone = :p")
                .param("p", phone)
                .query((rs, n) -> new UserRow(rs.getLong("id"), rs.getString("name"),
                        rs.getString("role"), rs.getObject("flat_id", Long.class)))
                .optional();
    }

    private void audit(Long actorId, String action, String phone) {
        String masked = "{\"phone\":\"******" + phone.substring(6) + "\"}";
        jdbc.sql("""
                INSERT INTO audit_log (actor_id, action, entity_type, entity_id, details)
                VALUES (:a, :act, 'USER', :e, CAST(:d AS jsonb))""")
                .param("a", actorId, Types.BIGINT)
                .param("act", action)
                .param("e", actorId == null ? null : String.valueOf(actorId), Types.VARCHAR)
                .param("d", masked)
                .update();
    }


}
