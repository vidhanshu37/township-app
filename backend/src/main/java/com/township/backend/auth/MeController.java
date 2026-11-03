package com.township.backend.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final JdbcClient jdbc;

    public MeController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return jdbc.sql("""
                SELECT u.id, u.name, u.role, u.resident_type, f.flat_no, f.tower
                FROM users u LEFT JOIN flats f ON f.id = u.flat_id
                WHERE u.id = :id""")
                .param("id", userId).query().singleRow();
    }
}