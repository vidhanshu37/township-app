package com.township.backend.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Types;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    public record Order(String orderId, BigDecimal amount) {}
    public record Result(String status, String message) {}
    private record OrderRow(long flatId, BigDecimal amount) {}

    private final JdbcClient jdbc;
    private final byte[] webhookSecret;

    public PaymentService(JdbcClient jdbc, @Value("${app.payments.webhook-secret}") String secret) {
        this.jdbc = jdbc;
        this.webhookSecret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public Order createOrder(long flatId, long userId, BigDecimal requested) {
        BigDecimal amount = requested;
        if (amount == null) {
            amount = jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM ledger_entry WHERE flat_id = :f")
                    .param("f", flatId).query(BigDecimal.class).single();
            if (amount.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to pay");
            }
        }
        String orderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        jdbc.sql("""
                INSERT INTO payment (flat_id, gateway_order_id, amount, created_by)
                VALUES (:f, :o, :a, :u)""")
                .param("f", flatId).param("o", orderId).param("a", amount).param("u", userId)
                .update();
        return new Order(orderId, amount);
    }

    public void assertOwnedBy(String orderId, long flatId) {
        if (findOrder(orderId).flatId() != flatId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    @Transactional
    public Result handlePaid(String orderId, String paymentId, String signature) {
        if (!validSignature(orderId, paymentId, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
        }
        OrderRow order = findOrder(orderId);

        jdbc.sql("SELECT id FROM flats WHERE id = :f FOR UPDATE")
                .param("f", order.flatId()).query(Long.class).single();

        int won;
        try {
            won = jdbc.sql("""
                    UPDATE payment SET status = 'SUCCESS', gateway_payment_id = :p, paid_at = now()
                    WHERE gateway_order_id = :o AND status = 'CREATED'""")
                    .param("p", paymentId).param("o", orderId).update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment id already used");
        }
        if (won == 0) {
            return new Result("DUPLICATE", "Order already processed");
        }

        List<PaymentAllocator.OpenBill> open = jdbc.sql("""
                        SELECT b.id, SUM(l.amount) AS outstanding
                        FROM bills b JOIN ledger_entry l ON l.bill_id = b.id
                        WHERE b.flat_id = :f
                        GROUP BY b.id, b.period
                        HAVING SUM(l.amount) > 0
                        ORDER BY b.period""")
                .param("f", order.flatId())
                .query((rs, n) -> new PaymentAllocator.OpenBill(rs.getLong("id"), rs.getBigDecimal("outstanding")))
                .list();

        for (PaymentAllocator.Line line : PaymentAllocator.allocate(open, order.amount())) {
            boolean advance = line.billId() == null;
            jdbc.sql("""
                    INSERT INTO ledger_entry (flat_id, bill_id, entry_type, amount, reference)
                    VALUES (:f, :b, :t, :a, :r)""")
                    .param("f", order.flatId())
                    .param("b", line.billId(), Types.BIGINT)
                    .param("t", advance ? "ADVANCE" : "PAYMENT")
                    .param("a", line.amount().negate())
                    .param("r", paymentId)
                    .update();
        }

        jdbc.sql("""
                INSERT INTO audit_log (actor_id, action, entity_type, entity_id, details)
                VALUES (NULL, 'PAYMENT_RECEIVED', 'PAYMENT', :id, CAST(:d AS jsonb))""")
                .param("id", paymentId)
                .param("d", "{\"flatId\":" + order.flatId() + ",\"amount\":" + order.amount() + "}")
                .update();
        return new Result("PROCESSED", "Payment recorded");
    }

    public String sign(String orderId, String paymentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean validSignature(String orderId, String paymentId, String signature) {
        return MessageDigest.isEqual(
                sign(orderId, paymentId).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private OrderRow findOrder(String orderId) {
        return jdbc.sql("SELECT flat_id, amount FROM payment WHERE gateway_order_id = :o")
                .param("o", orderId)
                .query((rs, n) -> new OrderRow(rs.getLong("flat_id"), rs.getBigDecimal("amount")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}