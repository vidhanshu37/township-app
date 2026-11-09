package com.township.backend.lookup;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class LookupService {

    public record FlatOption(long id, String flatNo, String tower, String residentName) {}

    public record PaymentOption(String paymentId, String orderId, String flatNo, BigDecimal amount,
                                String status, String source, OffsetDateTime paidAt) {}

    private final JdbcClient jdbc;

    public LookupService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<FlatOption> flats() {
        return jdbc.sql("""
                        SELECT f.id, f.flat_no, f.tower,
                               (SELECT string_agg(u.name, ', ') FROM users u
                                 WHERE u.flat_id = f.id AND u.role = 'RESIDENT') AS resident_name
                        FROM flats f
                        ORDER BY f.flat_no""")
                .query((rs, n) -> new FlatOption(rs.getLong("id"), rs.getString("flat_no"),
                        rs.getString("tower"), rs.getString("resident_name")))
                .list();
    }

    public List<PaymentOption> recentPayments(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return jdbc.sql("""
                        SELECT p.gateway_payment_id, p.gateway_order_id, f.flat_no, p.amount, p.status, p.paid_at
                        FROM payment p JOIN flats f ON f.id = p.flat_id
                        WHERE p.gateway_payment_id IS NOT NULL AND p.status IN ('SUCCESS', 'REVERSED')
                        ORDER BY p.paid_at DESC NULLS LAST, p.id DESC
                        LIMIT :l""")
                .param("l", safeLimit)
                .query((rs, n) -> {
                    String paymentId = rs.getString("gateway_payment_id");
                    return new PaymentOption(paymentId, rs.getString("gateway_order_id"), rs.getString("flat_no"),
                            rs.getBigDecimal("amount"), rs.getString("status"),
                            paymentId.startsWith("bank_") ? "BANK" : "GATEWAY",
                            rs.getObject("paid_at", OffsetDateTime.class));
                }).list();
    }
}