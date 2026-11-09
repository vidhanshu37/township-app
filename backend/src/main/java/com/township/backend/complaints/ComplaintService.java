package com.township.backend.complaints;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ComplaintService {

    public record ComplaintView(long id, long flatId, String flatNo, String text, String category, String priority,
                                String status, String triageSource, String triageReason, Long duplicateOf,
                                Long assignedTo, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record EventView(String eventType, String fromStatus, String toStatus, String note, Long actorId,
                            OffsetDateTime createdAt) {}

    public record Detail(ComplaintView complaint, List<EventView> events) {}

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "OPEN", Set.of("IN_PROGRESS", "RESOLVED"),
            "IN_PROGRESS", Set.of("OPEN", "RESOLVED"),
            "RESOLVED", Set.of("OPEN"));

    private static final String SELECT = """
            SELECT c.id, c.flat_id, f.flat_no, c.text, c.category, c.priority, c.status, c.triage_source,
                   c.triage_reason, c.duplicate_of, c.assigned_to, c.created_at, c.updated_at
            FROM complaint c JOIN flats f ON f.id = c.flat_id""";

    private final JdbcClient jdbc;

    public ComplaintService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ComplaintView create(long flatId, long userId, String rawText, TriageEngine.Result triage) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.length() < 10 || text.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complaint must be 10 to 1000 characters");
        }

        Map<Long, Long> parentOf = new HashMap<>();
        List<DuplicateFinder.Candidate> open = jdbc.sql("""
                        SELECT id, duplicate_of, category, text, created_at FROM complaint
                        WHERE status <> 'RESOLVED' AND category = :c
                          AND created_at > now() - interval '48 hours'""")
                .param("c", triage.category().name())
                .query((rs, n) -> {
                    long id = rs.getLong("id");
                    long parent = rs.getLong("duplicate_of");
                    if (!rs.wasNull()) {
                        parentOf.put(id, parent);
                    }
                    return new DuplicateFinder.Candidate(id, rs.getString("category"), rs.getString("text"),
                            rs.getObject("created_at", OffsetDateTime.class).toInstant());
                }).list();

        Optional<DuplicateFinder.Candidate> dup =
                DuplicateFinder.find(text, triage.category().name(), open, Instant.now());
        Long duplicateOf = dup.map(c -> parentOf.getOrDefault(c.id(), c.id())).orElse(null);

        long id = jdbc.sql("""
                        INSERT INTO complaint (flat_id, created_by, text, category, priority, triage_source,
                                               triage_reason, duplicate_of)
                        VALUES (:f, :u, :t, :c, :p, :s, :r, :d) RETURNING id""")
                .param("f", flatId).param("u", userId).param("t", text)
                .param("c", triage.category().name()).param("p", triage.priority().name())
                .param("s", triage.source().name()).param("r", triage.reason())
                .param("d", duplicateOf, Types.BIGINT)
                .query(Long.class).single();

        String note = triage.reason() + (duplicateOf == null ? "" : "; linked as duplicate of #" + duplicateOf);
        addEvent(id, userId, "CREATED", null, "OPEN", note);
        return get(id);
    }

    @Transactional
    public ComplaintView changeStatus(long id, String newStatus, String note, long actorId) {
        if (!TRANSITIONS.containsKey(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + newStatus);
        }
        String current = jdbc.sql("SELECT status FROM complaint WHERE id = :id FOR UPDATE")
                .param("id", id).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (!TRANSITIONS.get(current).contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot move a complaint from " + current + " to " + newStatus);
        }
        jdbc.sql("""
                        UPDATE complaint
                        SET status = :s, updated_at = now(),
                            assigned_to = CASE WHEN :s = 'IN_PROGRESS' THEN :u ELSE assigned_to END,
                            resolved_at = CASE WHEN :s = 'RESOLVED' THEN now() ELSE NULL END
                        WHERE id = :id""")
                .param("s", newStatus).param("u", actorId).param("id", id).update();
        addEvent(id, actorId, "STATUS_CHANGED", current, newStatus, note);
        return get(id);
    }

    public ComplaintView get(long id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id").param("id", id)
                .query(ComplaintService::view).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
    }

    public Detail detail(long id) {
        ComplaintView view = get(id);
        List<EventView> events = jdbc.sql("""
                        SELECT event_type, from_status, to_status, note, actor_id, created_at
                        FROM complaint_event WHERE complaint_id = :id ORDER BY id""")
                .param("id", id)
                .query((rs, n) -> {
                    long actor = rs.getLong("actor_id");
                    Long actorId = rs.wasNull() ? null : actor;
                    return new EventView(rs.getString("event_type"), rs.getString("from_status"),
                            rs.getString("to_status"), rs.getString("note"), actorId,
                            rs.getObject("created_at", OffsetDateTime.class));
                }).list();
        return new Detail(view, events);
    }

    public List<ComplaintView> listForFlat(long flatId) {
        return jdbc.sql(SELECT + " WHERE c.flat_id = :f ORDER BY c.created_at DESC LIMIT 100")
                .param("f", flatId).query(ComplaintService::view).list();
    }

    public List<ComplaintView> listAll(String status, String priority, String category, int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return jdbc.sql(SELECT + " " + """
                         WHERE (CAST(:s AS VARCHAR) IS NULL OR c.status = CAST(:s AS VARCHAR))
                           AND (CAST(:p AS VARCHAR) IS NULL OR c.priority = CAST(:p AS VARCHAR))
                           AND (CAST(:c AS VARCHAR) IS NULL OR c.category = CAST(:c AS VARCHAR))
                         ORDER BY CASE c.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, c.created_at
                         LIMIT :l OFFSET :o""")
                .param("s", status, Types.VARCHAR).param("p", priority, Types.VARCHAR)
                .param("c", category, Types.VARCHAR).param("l", safeLimit).param("o", Math.max(offset, 0))
                .query(ComplaintService::view).list();
    }

    private void addEvent(long complaintId, long actorId, String type, String from, String to, String note) {
        jdbc.sql("""
                        INSERT INTO complaint_event (complaint_id, actor_id, event_type, from_status, to_status, note)
                        VALUES (:c, :a, :t, :f, :to, :n)""")
                .param("c", complaintId).param("a", actorId).param("t", type)
                .param("f", from, Types.VARCHAR).param("to", to, Types.VARCHAR).param("n", note, Types.VARCHAR)
                .update();
    }

    private static ComplaintView view(ResultSet rs, int i) throws SQLException {
        long dup = rs.getLong("duplicate_of");
        Long duplicateOf = rs.wasNull() ? null : dup;
        long asg = rs.getLong("assigned_to");
        Long assignedTo = rs.wasNull() ? null : asg;
        return new ComplaintView(rs.getLong("id"), rs.getLong("flat_id"), rs.getString("flat_no"),
                rs.getString("text"), rs.getString("category"), rs.getString("priority"), rs.getString("status"),
                rs.getString("triage_source"), rs.getString("triage_reason"), duplicateOf, assignedTo,
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
}