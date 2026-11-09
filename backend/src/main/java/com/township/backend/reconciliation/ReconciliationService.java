package com.township.backend.reconciliation;

import com.township.backend.billing.PaymentService;
import com.township.backend.matching.BankCsvParser;
import com.township.backend.matching.BankRow;
import com.township.backend.matching.FlatSnapshot;
import com.township.backend.matching.Matcher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReconciliationService {

    public record Summary(long fileId, int totalRows, int credits, int debitsIgnored, int duplicates,
                          int autoMatched, int needsReview, int unmatched,
                          List<BankCsvParser.RowError> errors) {}

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 5000;
    private static final long UPLOAD_LOCK_KEY = 7_001L;

    private final JdbcClient jdbc;
    private final PaymentService payments;

    public ReconciliationService(JdbcClient jdbc, PaymentService payments) {
        this.jdbc = jdbc;
        this.payments = payments;
    }

    @Transactional
    public Summary upload(String filename, byte[] content, long actorId) {
        if (content.length == 0 || content.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be 1 byte to 2 MB");
        }
        BankCsvParser.Result parsed;
        try {
            parsed = BankCsvParser.parse(new String(content, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (parsed.credits().size() + parsed.errors().size() > MAX_ROWS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many rows (max " + MAX_ROWS + ")");
        }

        jdbc.sql("SELECT pg_advisory_xact_lock(:k)").param("k", UPLOAD_LOCK_KEY).query((rs, n) -> 1).single();

        String sha = sha256(content);
        boolean seen = jdbc.sql("SELECT EXISTS (SELECT 1 FROM bank_statement_file WHERE sha256 = :h)")
                .param("h", sha).query(Boolean.class).single();
        if (seen) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This file was already uploaded");
        }

        long fileId = jdbc.sql("""
                INSERT INTO bank_statement_file (filename, sha256, content, uploaded_by)
                VALUES (:n, :h, :c, :u) RETURNING id""")
                .param("n", filename).param("h", sha).param("c", content).param("u", actorId)
                .query(Long.class).single();

        Map<Long, FlatSnapshot> snapshots = new LinkedHashMap<>();
        for (FlatSnapshot s : loadSnapshots(null)) {
            snapshots.put(s.flatId(), s);
        }

        int duplicates = 0, auto = 0, review = 0, unmatched = 0;
        for (BankRow row : parsed.credits()) {
            Matcher.Decision d = Matcher.decide(row, new ArrayList<>(snapshots.values()));
            Optional<Long> txnId = insertTxn(fileId, row, d);
            if (txnId.isEmpty()) {
                duplicates++;
                continue;
            }
            switch (d.status()) {
                case AUTO_MATCHED -> {
                    long flatId = d.flatId();
                    lockFlat(flatId);
                    postBankPayment(txnId.get(), flatId, row.credit(), actorId, d.score(), "BANK_AUTO_MATCH");
                    loadSnapshots(flatId).forEach(s -> snapshots.put(s.flatId(), s));
                    auto++;
                }
                case NEEDS_REVIEW -> review++;
                default -> unmatched++;
            }
        }

        int total = parsed.credits().size() + parsed.debitsIgnored() + parsed.errors().size();
        jdbc.sql("""
                UPDATE bank_statement_file
                SET total_rows = :t, credits = :c, debits_ignored = :d, duplicates = :dup,
                    auto_matched = :a, needs_review = :r, unmatched = :u, errors = :e
                WHERE id = :id""")
                .param("t", total).param("c", parsed.credits().size()).param("d", parsed.debitsIgnored())
                .param("dup", duplicates).param("a", auto).param("r", review).param("u", unmatched)
                .param("e", parsed.errors().size()).param("id", fileId).update();

        return new Summary(fileId, total, parsed.credits().size(), parsed.debitsIgnored(), duplicates,
                auto, review, unmatched, parsed.errors());
    }

    private Optional<Long> insertTxn(long fileId, BankRow row, Matcher.Decision d) {
        return jdbc.sql("""
                INSERT INTO bank_txn (file_id, line_no, txn_date, narration, amount, utr, fingerprint,
                                      status, flat_id, score, reasons, note)
                VALUES (:f, :l, :d, :n, :a, :u, :fp, :s, :flat, :sc, :r, :note)
                ON CONFLICT DO NOTHING
                RETURNING id""")
                .param("f", fileId).param("l", row.lineNo()).param("d", row.date())
                .param("n", row.narration()).param("a", row.credit()).param("u", row.utr(), Types.VARCHAR)
                .param("fp", row.fingerprint()).param("s", d.status().name())
                .param("flat", d.flatId(), Types.BIGINT).param("sc", d.score())
                .param("r", String.join("; ", d.reasons())).param("note", d.note(), Types.VARCHAR)
                .query(Long.class).optional();
    }

    void lockFlat(long flatId) {
        jdbc.sql("SELECT id FROM flats WHERE id = :f FOR UPDATE")
                .param("f", flatId).query(Long.class).single();
    }

    void postBankPayment(long txnId, long flatId, BigDecimal amount, long actorId, int score, String action) {
        String paymentId = "bank_" + txnId;
        long pid = jdbc.sql("""
                INSERT INTO payment (flat_id, gateway_order_id, gateway_payment_id, amount, status, created_by, paid_at)
                VALUES (:f, :o, :p, :a, 'SUCCESS', :u, now()) RETURNING id""")
                .param("f", flatId).param("o", "bank_order_" + txnId).param("p", paymentId)
                .param("a", amount).param("u", actorId).query(Long.class).single();

        payments.allocateToLedger(flatId, paymentId, amount);

        jdbc.sql("UPDATE bank_txn SET payment_id = :p WHERE id = :id")
                .param("p", pid).param("id", txnId).update();
        jdbc.sql("""
                INSERT INTO audit_log (actor_id, action, entity_type, entity_id, details)
                VALUES (:u, :act, 'BANK_TXN', :id,
                        jsonb_build_object('flatId', CAST(:f AS bigint), 'amount', CAST(:a AS numeric),
                                           'score', CAST(:s AS int)))""")
                .param("u", actorId).param("act", action).param("id", String.valueOf(txnId))
                .param("f", flatId).param("a", amount).param("s", score).update();
    }

    private List<FlatSnapshot> loadSnapshots(Long onlyFlat) {
        record FlatRow(long id, String flatNo, String payRef, String names) {}
        List<FlatRow> flats = jdbc.sql("""
                        SELECT f.id, f.flat_no, f.pay_ref,
                               (SELECT string_agg(u.name, ' ') FROM users u
                                 WHERE u.flat_id = f.id AND u.role = 'RESIDENT') AS names
                        FROM flats f
                        WHERE CAST(:f AS BIGINT) IS NULL OR f.id = CAST(:f AS BIGINT)
                        ORDER BY f.id""")
                .param("f", onlyFlat, Types.BIGINT)
                .query((rs, n) -> new FlatRow(rs.getLong("id"), rs.getString("flat_no"),
                        rs.getString("pay_ref"), rs.getString("names")))
                .list();

        Map<Long, List<BigDecimal>> open = new LinkedHashMap<>();
        Map<Long, java.time.LocalDate> oldest = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT b.flat_id, b.period, SUM(l.amount) AS outstanding
                        FROM bills b JOIN ledger_entry l ON l.bill_id = b.id
                        WHERE CAST(:f AS BIGINT) IS NULL OR b.flat_id = CAST(:f AS BIGINT)
                        GROUP BY b.id, b.flat_id, b.period
                        HAVING SUM(l.amount) > 0
                        ORDER BY b.flat_id, b.period""")
                .param("f", onlyFlat, Types.BIGINT)
                .query((rs, n) -> {
                    long flatId = rs.getLong("flat_id");
                    open.computeIfAbsent(flatId, k -> new ArrayList<>()).add(rs.getBigDecimal("outstanding"));
                    oldest.putIfAbsent(flatId, rs.getDate("period").toLocalDate());
                    return flatId;
                }).list();

        Map<Long, List<String>> upi = new LinkedHashMap<>();
        jdbc.sql(UpiSql.LOAD)
                .param("f", onlyFlat, Types.BIGINT)
                .query((rs, n) -> {
                    upi.computeIfAbsent(rs.getLong("flat_id"), k -> new ArrayList<>()).add(rs.getString("upi_id"));
                    return 0;
                }).list();

        List<FlatSnapshot> out = new ArrayList<>();
        for (FlatRow f : flats) {
            out.add(new FlatSnapshot(f.id(), f.flatNo(), f.payRef(), f.names(),
                    open.getOrDefault(f.id(), List.of()), oldest.get(f.id()), upi.getOrDefault(f.id(), List.of())));
        }
        return out;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}