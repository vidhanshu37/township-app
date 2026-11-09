package com.township.backend.reconciliation;

import com.township.backend.matching.UpiId;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class ReviewService {

    public record TxnView(long id, long fileId, int lineNo, LocalDate date, String narration, BigDecimal amount,
                          String utr, String status, Long flatId, String flatNo, int score, String reasons,
                          String note, String resolutionNote, String paymentId, String paymentStatus) {}

    private record Row(BigDecimal amount, int score, String narration) {}

    private static final Set<String> STATUSES =
            Set.of("AUTO_MATCHED", "NEEDS_REVIEW", "UNMATCHED", "CONFIRMED", "REJECTED");

    private final JdbcClient jdbc;
    private final ReconciliationService reconciliation;

    public ReviewService(JdbcClient jdbc, ReconciliationService reconciliation) {
        this.jdbc = jdbc;
        this.reconciliation = reconciliation;
    }

    public List<TxnView> list(String status, int limit, int offset) {
        if (!STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return jdbc.sql(SELECT + " WHERE t.status = :s ORDER BY t.score DESC, t.id LIMIT :l OFFSET :o")
                .param("s", status).param("l", safeLimit).param("o", Math.max(offset, 0))
                .query(ReviewService::view).list();
    }

    @Transactional
    public TxnView confirm(long txnId, long flatId, String note, long actorId) {
        boolean flatExists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM flats WHERE id = :f)")
                .param("f", flatId).query(Boolean.class).single();
        if (!flatExists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown flat");
        }
        reconciliation.lockFlat(flatId);

        Row row = jdbc.sql("""
                        UPDATE bank_txn
                        SET status = 'CONFIRMED', flat_id = :f, resolved_by = :u, resolved_at = now(),
                            resolution_note = :n
                        WHERE id = :id AND status IN ('NEEDS_REVIEW', 'UNMATCHED')
                        RETURNING amount, score, narration""")
                .param("f", flatId).param("u", actorId).param("n", note, java.sql.Types.VARCHAR).param("id", txnId)
                .query((rs, i) -> new Row(rs.getBigDecimal("amount"), rs.getInt("score"), rs.getString("narration")))
                .optional()
                .orElseThrow(() -> notResolvable(txnId));

        reconciliation.postBankPayment(txnId, flatId, row.amount(), actorId, row.score(), "BANK_CONFIRMED");
        for (String upiId : UpiId.extract(row.narration())) {
            jdbc.sql(UpiSql.LEARN).param("f", flatId).param("u", upiId).update();
        }
        return get(txnId);
    }

    @Transactional
    public TxnView reject(long txnId, String reason, long actorId) {
        int done = jdbc.sql("""
                        UPDATE bank_txn
                        SET status = 'REJECTED', resolved_by = :u, resolved_at = now(), resolution_note = :r
                        WHERE id = :id AND status IN ('NEEDS_REVIEW', 'UNMATCHED')""")
                .param("u", actorId).param("r", reason).param("id", txnId).update();
        if (done == 0) {
            throw notResolvable(txnId);
        }
        jdbc.sql("""
                INSERT INTO audit_log (actor_id, action, entity_type, entity_id, details)
                VALUES (:u, 'BANK_REJECTED', 'BANK_TXN', :id,
                        jsonb_build_object('reason', CAST(:r AS text)))""")
                .param("u", actorId).param("id", String.valueOf(txnId)).param("r", reason).update();
        return get(txnId);
    }

    public TxnView get(long txnId) {
        return jdbc.sql(SELECT + " WHERE t.id = :id").param("id", txnId)
                .query(ReviewService::view).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank transaction not found"));
    }

    private ResponseStatusException notResolvable(long txnId) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM bank_txn WHERE id = :id)")
                .param("id", txnId).query(Boolean.class).single();
        return exists
                ? new ResponseStatusException(HttpStatus.CONFLICT, "Already resolved or auto-matched")
                : new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank transaction not found");
    }

    private static final String SELECT = """
            SELECT t.id, t.file_id, t.line_no, t.txn_date, t.narration, t.amount, t.utr, t.status,
                   t.flat_id, f.flat_no, t.score, t.reasons, t.note, t.resolution_note,
                   p.gateway_payment_id, p.status AS payment_status
            FROM bank_txn t
            LEFT JOIN flats f ON f.id = t.flat_id
            LEFT JOIN payment p ON p.id = t.payment_id""";

    private static TxnView view(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        long flat = rs.getLong("flat_id");
        Long flatId = rs.wasNull() ? null : flat;
        return new TxnView(rs.getLong("id"), rs.getLong("file_id"), rs.getInt("line_no"),
                rs.getDate("txn_date").toLocalDate(), rs.getString("narration"), rs.getBigDecimal("amount"),
                rs.getString("utr"), rs.getString("status"), flatId, rs.getString("flat_no"),
                rs.getInt("score"), rs.getString("reasons"), rs.getString("note"),
                rs.getString("resolution_note"), rs.getString("gateway_payment_id"),
                rs.getString("payment_status"));
    }
}