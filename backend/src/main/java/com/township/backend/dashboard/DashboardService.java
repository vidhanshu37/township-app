package com.township.backend.dashboard;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    public record Money(BigDecimal periodBilled, BigDecimal periodOutstanding, BigDecimal periodCollectionRatePercent,
                        BigDecimal collectedInPeriod, BigDecimal totalDues, BigDecimal advanceCredit) {}

    public record Overdue(BigDecimal amount, long flats, BigDecimal days1to30, BigDecimal days31to60,
                          BigDecimal days61plus) {}

    public record Defaulter(String flatNo, BigDecimal overdueAmount, LocalDate oldestDueDate, int daysOverdue) {}

    public record Reconciliation(Map<String, Long> countByStatus, long pendingReview, BigDecimal unattributedAmount) {}

    public record Complaints(Map<String, Long> byStatus, Map<String, Long> openByPriority,
                             Map<String, Long> openByCategory, long openHighPriority, long openLinkedDuplicates,
                             BigDecimal avgResolutionHoursLast30Days) {}

    public record Dashboard(String period, LocalDate asOf, Money money, Overdue overdue,
                            List<Defaulter> topDefaulters, Reconciliation reconciliation, Complaints complaints) {}

    private static final List<String> BANK_STATUSES =
            List.of("AUTO_MATCHED", "NEEDS_REVIEW", "UNMATCHED", "CONFIRMED", "REJECTED");
    private static final List<String> COMPLAINT_STATUSES = List.of("OPEN", "IN_PROGRESS", "RESOLVED");
    private static final List<String> PRIORITIES = List.of("HIGH", "MEDIUM", "LOW");
    private static final List<String> CATEGORIES =
            List.of("PLUMBING", "ELECTRICAL", "LIFT", "SECURITY", "SAFETY", "HOUSEKEEPING", "PARKING", "NOISE", "OTHER");

    private final JdbcClient jdbc;

    public DashboardService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Dashboard build(YearMonth period, LocalDate today) {
        LocalDate start = period.atDay(1);
        LocalDate nextStart = period.plusMonths(1).atDay(1);
        return new Dashboard(period.toString(), today, money(start, nextStart, today), overdue(today),
                topDefaulters(today), reconciliation(), complaints());
    }

    private Money money(LocalDate start, LocalDate nextStart, LocalDate today) {
        // bills of the chosen month and how much of them is still unpaid
        record Period(BigDecimal billed, BigDecimal outstanding) {}
        Period p = jdbc.sql("""
                        SELECT COALESCE(SUM(b.amount), 0) AS billed,
                               COALESCE(SUM(GREATEST(o.outstanding, 0)), 0) AS outstanding
                        FROM bills b
                        LEFT JOIN (SELECT bill_id, SUM(amount) AS outstanding FROM ledger_entry
                                   WHERE bill_id IS NOT NULL GROUP BY bill_id) o ON o.bill_id = b.id
                        WHERE b.period = CAST(:start AS date)""")
                .param("start", start)
                .query((rs, n) -> new Period(rs.getBigDecimal("billed"), rs.getBigDecimal("outstanding")))
                .single();

        BigDecimal rate = p.billed().signum() == 0
                ? BigDecimal.ZERO.setScale(1)
                : p.billed().subtract(p.outstanding()).multiply(BigDecimal.valueOf(100))
                .divide(p.billed(), 1, RoundingMode.HALF_UP);

        // net cash received in the month: payments and advances, minus reversals
        BigDecimal collected = jdbc.sql("""
                        SELECT COALESCE(-SUM(amount), 0) FROM ledger_entry
                        WHERE entry_type IN ('PAYMENT', 'ADVANCE', 'REVERSAL')
                          AND created_at >= (CAST(:start AS timestamp) AT TIME ZONE 'Asia/Kolkata')
                          AND created_at <  (CAST(:end AS timestamp) AT TIME ZONE 'Asia/Kolkata')""")
                .param("start", start).param("end", nextStart)
                .query(BigDecimal.class).single();

        BigDecimal totalDues = jdbc.sql("""
                        SELECT COALESCE(SUM(out), 0) FROM (
                            SELECT SUM(amount) AS out FROM ledger_entry WHERE bill_id IS NOT NULL
                            GROUP BY bill_id HAVING SUM(amount) > 0) t""")
                .query(BigDecimal.class).single();

        BigDecimal advance = jdbc.sql("""
                        SELECT GREATEST(COALESCE(-SUM(amount), 0), 0) FROM ledger_entry WHERE bill_id IS NULL""")
                .query(BigDecimal.class).single();

        return new Money(p.billed(), p.outstanding(), rate, collected, totalDues, advance);
    }

    private Overdue overdue(LocalDate today) {
        return jdbc.sql("""
                        WITH open_bills AS (
                            SELECT b.flat_id, b.due_date, SUM(l.amount) AS out
                            FROM bills b JOIN ledger_entry l ON l.bill_id = b.id
                            GROUP BY b.id, b.flat_id, b.due_date HAVING SUM(l.amount) > 0)
                        SELECT COALESCE(SUM(out), 0) AS amount,
                               COUNT(DISTINCT flat_id) AS flats,
                               COALESCE(SUM(out) FILTER (WHERE CAST(:today AS date) - due_date BETWEEN 1 AND 30), 0) AS d30,
                               COALESCE(SUM(out) FILTER (WHERE CAST(:today AS date) - due_date BETWEEN 31 AND 60), 0) AS d60,
                               COALESCE(SUM(out) FILTER (WHERE CAST(:today AS date) - due_date > 60), 0) AS d61
                        FROM open_bills WHERE due_date < CAST(:today AS date)""")
                .param("today", today)
                .query((rs, n) -> new Overdue(rs.getBigDecimal("amount"), rs.getLong("flats"),
                        rs.getBigDecimal("d30"), rs.getBigDecimal("d60"), rs.getBigDecimal("d61")))
                .single();
    }

    private List<Defaulter> topDefaulters(LocalDate today) {
        return jdbc.sql("""
                        WITH open_bills AS (
                            SELECT b.flat_id, b.due_date, SUM(l.amount) AS out
                            FROM bills b JOIN ledger_entry l ON l.bill_id = b.id
                            GROUP BY b.id, b.flat_id, b.due_date HAVING SUM(l.amount) > 0)
                        SELECT f.flat_no, SUM(o.out) AS overdue, MIN(o.due_date) AS oldest
                        FROM open_bills o JOIN flats f ON f.id = o.flat_id
                        WHERE o.due_date < CAST(:today AS date)
                        GROUP BY f.id, f.flat_no
                        ORDER BY overdue DESC, f.flat_no
                        LIMIT 5""")
                .param("today", today)
                .query((rs, n) -> {
                    LocalDate oldest = rs.getDate("oldest").toLocalDate();
                    return new Defaulter(rs.getString("flat_no"), rs.getBigDecimal("overdue"), oldest,
                            (int) java.time.temporal.ChronoUnit.DAYS.between(oldest, today));
                }).list();
    }

    private Reconciliation reconciliation() {
        Map<String, Long> counts = zeroed(BANK_STATUSES);
        jdbc.sql("SELECT status, COUNT(*) AS n FROM bank_txn GROUP BY status")
                .query((rs, n) -> counts.put(rs.getString("status"), rs.getLong("n"))).list();
        // money that arrived in the bank but is not yet attributed to a flat
        BigDecimal unattributed = jdbc.sql("""
                        SELECT COALESCE(SUM(amount), 0) FROM bank_txn WHERE status IN ('NEEDS_REVIEW', 'UNMATCHED')""")
                .query(BigDecimal.class).single();
        return new Reconciliation(counts, counts.get("NEEDS_REVIEW"), unattributed);
    }

    private Complaints complaints() {
        Map<String, Long> byStatus = zeroed(COMPLAINT_STATUSES);
        jdbc.sql("SELECT status, COUNT(*) AS n FROM complaint GROUP BY status")
                .query((rs, n) -> byStatus.put(rs.getString("status"), rs.getLong("n"))).list();

        Map<String, Long> byPriority = zeroed(PRIORITIES);
        jdbc.sql("SELECT priority, COUNT(*) AS n FROM complaint WHERE status <> 'RESOLVED' GROUP BY priority")
                .query((rs, n) -> byPriority.put(rs.getString("priority"), rs.getLong("n"))).list();

        Map<String, Long> byCategory = zeroed(CATEGORIES);
        jdbc.sql("SELECT category, COUNT(*) AS n FROM complaint WHERE status <> 'RESOLVED' GROUP BY category")
                .query((rs, n) -> byCategory.put(rs.getString("category"), rs.getLong("n"))).list();

        long linked = jdbc.sql("SELECT COUNT(*) FROM complaint WHERE status <> 'RESOLVED' AND duplicate_of IS NOT NULL")
                .query(Long.class).single();

        BigDecimal avgHours = jdbc.sql("""
                        SELECT COALESCE(ROUND(AVG(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0)::numeric, 1), 0)
                        FROM complaint
                        WHERE resolved_at IS NOT NULL AND resolved_at > now() - interval '30 days'""")
                .query(BigDecimal.class).single();

        return new Complaints(byStatus, byPriority, byCategory, byPriority.get("HIGH"), linked, avgHours);
    }

    private static Map<String, Long> zeroed(List<String> keys) {
        Map<String, Long> m = new LinkedHashMap<>();
        keys.forEach(k -> m.put(k, 0L));
        return m;
    }
}