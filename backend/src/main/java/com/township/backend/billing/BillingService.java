package com.township.backend.billing;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BillingService {
    public record BillView(long id, LocalDate period, BigDecimal amount, LocalDate dueDate,
                           BigDecimal outstanding, long daysOverdue, String status) {}
    public record Dues(BigDecimal balance, List<BillView> bills) {}
    public record LedgerRow(long id, String entryType, BigDecimal amount,
                            String reference, OffsetDateTime createdAt) {}

    private final JdbcClient jdbc;
    private final BigDecimal ratePerSqft;
    private final int dueDay;
    private final BigDecimal lateFeeRate;

    public BillingService(JdbcClient jdbc,
                          @Value("${app.billing.rate-per-sqft}") BigDecimal ratePerSqft,
                          @Value("${app.billing.due-day}") int dueDay,
                          @Value("${app.billing.late-fee-rate}") BigDecimal lateFeeRate) {
        this.jdbc = jdbc;
        this.ratePerSqft = ratePerSqft;
        this.dueDay = dueDay;
        this.lateFeeRate = lateFeeRate;
    }

    @Transactional
    public int applyLateFees(long actorId) {
        LocalDate today = LocalDate.now();

        // ek time par ek hi run (parallel run se double posting nahi)
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(1001)) t")
                .query(Integer.class).single();

        record Row(long billId, long flatId, LocalDate from, BigDecimal principal) {}

        List<Row> rows = jdbc.sql("""
                SELECT b.id, b.flat_id,
                       COALESCE(b.late_fee_through, b.due_date) AS from_date,
                       COALESCE(SUM(l.amount) FILTER (WHERE l.entry_type <> 'LATE_FEE'), 0) AS principal
                FROM bills b
                LEFT JOIN ledger_entry l ON l.bill_id = b.id
                WHERE b.due_date < CAST(:today AS date)
                GROUP BY b.id
                HAVING COALESCE(SUM(l.amount) FILTER (WHERE l.entry_type <> 'LATE_FEE'), 0) > 0""")
                .param("today", today)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getLong("flat_id"),
                        rs.getObject("from_date", LocalDate.class), rs.getBigDecimal("principal")))
                .list();

        int posted = 0;
        for (Row r : rows) {
            if (!today.isAfter(r.from())) {
                continue;
            }
            BigDecimal fee = LateFeeCalculator.calculate(r.principal(), r.from(), today, lateFeeRate);
            if (fee.signum() > 0) {
                jdbc.sql("""
                        INSERT INTO ledger_entry (flat_id, bill_id, entry_type, amount, reference, created_by)
                        VALUES (:f, :b, 'LATE_FEE', :a, :ref, :u)""")
                        .param("f", r.flatId()).param("b", r.billId()).param("a", fee)
                        .param("ref", "LATEFEE-" + today).param("u", actorId)
                        .update();
                posted++;
            }
            jdbc.sql("UPDATE bills SET late_fee_through = CAST(:d AS date) WHERE id = :id")
                    .param("d", today).param("id", r.billId()).update();
        }

        jdbc.sql("""
                INSERT INTO audit_log (actor_id, action, entity_type, entity_id, details)
                VALUES (:a, 'LATE_FEES_APPLIED', 'BILL_RUN', :d, CAST(:x AS jsonb))""")
                .param("a", actorId).param("d", today.toString())
                .param("x", "{\"posted\":" + posted + "}")
                .update();
        return posted;
    }

    @Transactional
    public int generate(YearMonth period, long actorId) {
        LocalDate periodDate = period.atDay(1);
        LocalDate due = period.atDay(dueDay);

        int created = jdbc.sql("""
                INSERT INTO bills (flat_id, period, amount, due_date)
                SELECT id, CAST(:period AS date), ROUND(area_sqft * CAST(:rate AS numeric), 2), CAST(:due AS date)
                FROM flats
                ON CONFLICT (flat_id, period) DO NOTHING""")
                .param("period", periodDate).param("rate", ratePerSqft).param("due", due)
                .update();

        jdbc.sql("""
                INSERT INTO ledger_entry (flat_id, bill_id, entry_type, amount, reference, created_by)
                SELECT b.flat_id, b.id, 'BILL', b.amount, 'BILL-' || to_char(b.period, 'YYYY-MM'), :actor
                FROM bills b
                WHERE b.period = CAST(:period AS date)
                  AND NOT EXISTS (SELECT 1 FROM ledger_entry l
                                  WHERE l.bill_id = b.id AND l.entry_type = 'BILL')""")
                .param("period", periodDate).param("actor", actorId)
                .update();

        jdbc.sql("""
                INSERT INTO audit_log (actor_id, action, entity_type, entity_id, details)
                VALUES (:a, 'BILLS_GENERATED', 'BILL_RUN', :p, CAST(:d AS jsonb))""")
                .param("a", actorId)
                .param("p", period.toString())
                .param("d", "{\"created\":" + created + "}")
                .update();
        return created;
    }

    public Dues dues(long flatId) {
        BigDecimal balance = jdbc.sql(
                        "SELECT COALESCE(SUM(amount), 0) FROM ledger_entry WHERE flat_id = :f")
                .param("f", flatId).query(BigDecimal.class).single();

        List<BillView> bills = jdbc.sql("""
                SELECT b.id, b.period, b.amount, b.due_date,
                       COALESCE(SUM(l.amount), 0) AS outstanding
                FROM bills b
                LEFT JOIN ledger_entry l ON l.bill_id = b.id
                WHERE b.flat_id = :f
                GROUP BY b.id
                ORDER BY b.period DESC""")
                .param("f", flatId)
                .query((rs, n) -> {
                    LocalDate due = rs.getObject("due_date", LocalDate.class);
                    BigDecimal out = rs.getBigDecimal("outstanding");
                    long overdue = out.signum() > 0
                            ? Math.max(0, ChronoUnit.DAYS.between(due, LocalDate.now())) : 0;
                    String status = out.signum() <= 0 ? "PAID" : overdue > 0 ? "OVERDUE" : "DUE";
                    return new BillView(rs.getLong("id"), rs.getObject("period", LocalDate.class),
                            rs.getBigDecimal("amount"), due, out, overdue, status);
                }).list();
        return new Dues(balance, bills);
    }

    public List<LedgerRow> ledger(long flatId) {
        return jdbc.sql("""
                SELECT id, entry_type, amount, reference, created_at
                FROM ledger_entry WHERE flat_id = :f
                ORDER BY created_at DESC, id DESC LIMIT 100""")
                .param("f", flatId)
                .query((rs, n) -> new LedgerRow(rs.getLong("id"), rs.getString("entry_type"),
                        rs.getBigDecimal("amount"), rs.getString("reference"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

}
