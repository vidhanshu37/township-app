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
                                Long assignedTo, String assignedToName, boolean escalated,
                                OffsetDateTime responseDueAt, OffsetDateTime firstResponseAt,
                                boolean responseBreached, Integer rating, String ratingComment,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record EventView(String eventType, String fromStatus, String toStatus, String note, Long actorId,
                            OffsetDateTime createdAt) {
    }

    public record Detail(ComplaintView complaint, List<EventView> events) {
    }

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "OPEN", Set.of("IN_PROGRESS", "RESOLVED"),
            "IN_PROGRESS", Set.of("OPEN", "RESOLVED"),
            "RESOLVED", Set.of("OPEN"));

    private final JdbcClient jdbc;

    public ComplaintService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ComplaintView create(long flatId, long userId, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.length() < 10 || text.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complaint must be 10 to 1000 characters");
        }
        TriageEngine.Result triage = TriageEngine.classify(text);
        boolean escalated = triage.source() == TriageEngine.Source.SAFETY_RULE;

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

        Long assignee = escalated
                ? jdbc.sql(ComplaintSql.DUTY_MANAGER).query(Long.class).optional().orElse(null)
                : null;
        if (assignee == null) {
            assignee = jdbc.sql(ComplaintSql.ROUTED_TO).param("c", triage.category().name())
                    .query(Long.class).optional().orElse(null);
        }

        long id = jdbc.sql(ComplaintSql.INSERT)
                .param("f", flatId).param("u", userId).param("t", text)
                .param("c", triage.category().name()).param("p", triage.priority().name())
                .param("s", triage.source().name()).param("r", triage.reason())
                .param("d", duplicateOf, Types.BIGINT).param("a", assignee, Types.BIGINT)
                .param("e", escalated).param("h", ComplaintPolicy.responseHours(triage.priority()))
                .query(Long.class).single();

        String note = triage.reason() + (duplicateOf == null ? "" : "; linked as duplicate of #" + duplicateOf);
        addEvent(id, userId, "CREATED", null, "OPEN", note);
        if (assignee != null) {
            String who = jdbc.sql("SELECT name FROM users WHERE id = :id").param("id", assignee)
                    .query(String.class).single();
            addEvent(id, null, "ASSIGNED", null, null,
                    (escalated ? "escalated to the duty manager: " : "assigned to ") + who);
        }
        return get(id);
    }

    @Transactional
    public ComplaintView changeStatus(long id, String newStatus, String note, long actorId) {
        if (!TRANSITIONS.containsKey(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + newStatus);
        }
        String current = jdbc.sql(ComplaintSql.LOCK_STATUS)
                .param("id", id).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (!TRANSITIONS.get(current).contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot move a complaint from " + current + " to " + newStatus);
        }
        jdbc.sql(ComplaintSql.UPDATE_STATUS)
                .param("s", newStatus).param("u", actorId).param("id", id).update();
        addEvent(id, actorId, "STATUS_CHANGED", current, newStatus, note);
        return get(id);
    }

    @Transactional
    public ComplaintView changeCategory(long id, String newCategory, long actorId) {
        TriageEngine.Category category;
        try {
            category = TriageEngine.Category.valueOf(newCategory == null ? "" : newCategory);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category: " + newCategory);
        }
        String old = jdbc.sql(ComplaintSql.LOCK_FOR_CATEGORY).param("id", id)
                .query((rs, n) -> rs.getString("category")).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (old.equals(category.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already in category " + old);
        }
        jdbc.sql(ComplaintSql.UPDATE_CATEGORY).param("c", category.name()).param("id", id).update();
        addEvent(id, actorId, "CATEGORY_CHANGED", null, null, old + " to " + category.name());
        return get(id);
    }

    @Transactional
    public ComplaintView rate(long id, long flatId, long userId, int rating, String comment) {
        if (!ComplaintPolicy.validRating(rating)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be from 1 to 5");
        }
        record Row(String status, long flatId, boolean rated) {
        }
        Row row = jdbc.sql(ComplaintSql.LOCK_FOR_RATING).param("id", id)
                .query((rs, n) -> new Row(rs.getString("status"), rs.getLong("flat_id"), rs.getBoolean("rated")))
                .optional()
                .filter(r -> r.flatId() == flatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (!"RESOLVED".equals(row.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can rate a complaint once it is resolved");
        }
        if (row.rated()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This complaint is already rated");
        }
        String clean = comment == null || comment.isBlank() ? null : comment.trim();
        jdbc.sql(ComplaintSql.UPDATE_RATING).param("r", rating).param("m", clean, Types.VARCHAR)
                .param("id", id).update();
        addEvent(id, userId, "RATED", null, null, rating + "/5" + (clean == null ? "" : ": " + clean));
        return get(id);
    }

    public ComplaintView get(long id) {
        return jdbc.sql(ComplaintSql.SELECT + " WHERE c.id = :id").param("id", id)
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


    public Detail detailForFlat(long id, long flatId) {
        Detail d = detail(id);
        if (d.complaint().flatId() != flatId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
        return d;
    }

    public List<ComplaintView> listForFlat(long flatId) {
        return jdbc.sql(ComplaintSql.SELECT + " WHERE c.flat_id = :f ORDER BY c.created_at DESC LIMIT 100")
                .param("f", flatId).query(ComplaintService::view).list();
    }


    public List<ComplaintView> listAll(String status, String priority, String category, int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return jdbc.sql(ComplaintSql.SELECT + " " + ComplaintSql.LIST_ALL_WHERE)
                .param("s", status, Types.VARCHAR).param("p", priority, Types.VARCHAR)
                .param("c", category, Types.VARCHAR).param("l", safeLimit).param("o", Math.max(offset, 0))
                .query(ComplaintService::view).list();
    }

    private void addEvent(long complaintId, Long actorId, String type, String from, String to, String note) {
        jdbc.sql(ComplaintSql.INSERT_EVENT)
                .param("c", complaintId).param("a", actorId, Types.BIGINT).param("t", type)
                .param("f", from, Types.VARCHAR).param("to", to, Types.VARCHAR).param("n", note, Types.VARCHAR)
                .update();
    }

    private static ComplaintView view(ResultSet rs, int i) throws SQLException {
        long dup = rs.getLong("duplicate_of");
        Long duplicateOf = rs.wasNull() ? null : dup;
        long asg = rs.getLong("assigned_to");
        Long assignedTo = rs.wasNull() ? null : asg;
        int rt = rs.getInt("rating");
        Integer rating = rs.wasNull() ? null : rt;
        return new ComplaintView(rs.getLong("id"), rs.getLong("flat_id"), rs.getString("flat_no"),
                rs.getString("text"), rs.getString("category"), rs.getString("priority"), rs.getString("status"),
                rs.getString("triage_source"), rs.getString("triage_reason"), duplicateOf, assignedTo,
                rs.getString("assigned_name"), rs.getBoolean("escalated"),
                rs.getObject("response_due_at", OffsetDateTime.class),
                rs.getObject("first_response_at", OffsetDateTime.class), rs.getBoolean("response_breached"),
                rating, rs.getString("rating_comment"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
}