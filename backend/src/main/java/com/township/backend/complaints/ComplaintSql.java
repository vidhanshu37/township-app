package com.township.backend.complaints;

/** SQL text for complaints. Kept in plain strings so the assembled queries can be run directly in tests. */
final class ComplaintSql {

    private ComplaintSql() {}

    static final String SELECT = """
            SELECT c.id, c.flat_id, f.flat_no, c.text, c.category, c.priority, c.status, c.triage_source,
                   c.triage_reason, c.duplicate_of, c.assigned_to, a.name AS assigned_name,
                   c.escalated, c.response_due_at, c.first_response_at,
                   (COALESCE(c.first_response_at, now()) > c.response_due_at) AS response_breached,
                   c.rating, c.rating_comment, c.created_at, c.updated_at
            FROM complaint c
            JOIN flats f ON f.id = c.flat_id
            LEFT JOIN users a ON a.id = c.assigned_to""";

    static final String ROUTED_TO = "SELECT user_id FROM complaint_routing WHERE category = :c";

    static final String DUTY_MANAGER = "SELECT id FROM users WHERE duty_manager ORDER BY id LIMIT 1";

    static final String INSERT = """
            INSERT INTO complaint (flat_id, created_by, text, category, priority, triage_source, triage_reason,
                                   duplicate_of, assigned_to, escalated, response_due_at)
            VALUES (:f, :u, :t, :c, :p, :s, :r, :d, :a, :e, now() + make_interval(hours => :h))
            RETURNING id""";

    static final String LOCK_STATUS = "SELECT status FROM complaint WHERE id = :id FOR UPDATE";

    static final String UPDATE_STATUS = """
            UPDATE complaint
            SET status = :s, updated_at = now(),
                assigned_to = CASE WHEN :s = 'IN_PROGRESS' THEN :u ELSE assigned_to END,
                first_response_at = COALESCE(first_response_at, now()),
                resolved_at = CASE WHEN :s = 'RESOLVED' THEN now() ELSE NULL END,
                rating = CASE WHEN :s = 'OPEN' THEN NULL ELSE rating END,
                rating_comment = CASE WHEN :s = 'OPEN' THEN NULL ELSE rating_comment END,
                rated_at = CASE WHEN :s = 'OPEN' THEN NULL ELSE rated_at END
            WHERE id = :id""";

    static final String LOCK_FOR_CATEGORY =
            "SELECT category, status, escalated FROM complaint WHERE id = :id FOR UPDATE";

    static final String UPDATE_CATEGORY = """
            UPDATE complaint
            SET category = :c, triage_source = 'MANUAL',
                triage_reason = 'category changed by help desk: ' || category || ' to ' || :c,
                updated_at = now(),
                assigned_to = CASE WHEN status = 'OPEN' AND NOT escalated
                                   THEN COALESCE((SELECT user_id FROM complaint_routing WHERE category = :c), assigned_to)
                                   ELSE assigned_to END
            WHERE id = :id""";

    static final String LOCK_FOR_RATING =
            "SELECT status, flat_id, rated_at IS NOT NULL AS rated FROM complaint WHERE id = :id FOR UPDATE";

    static final String UPDATE_RATING = """
            UPDATE complaint SET rating = :r, rating_comment = :m, rated_at = now(), updated_at = now()
            WHERE id = :id""";

    static final String INSERT_EVENT = """
            INSERT INTO complaint_event (complaint_id, actor_id, event_type, from_status, to_status, note)
            VALUES (:c, :a, :t, :f, :to, :n)""";

    static final String LIST_ALL_WHERE = """
             WHERE (CAST(:s AS VARCHAR) IS NULL OR c.status = CAST(:s AS VARCHAR))
               AND (CAST(:p AS VARCHAR) IS NULL OR c.priority = CAST(:p AS VARCHAR))
               AND (CAST(:c AS VARCHAR) IS NULL OR c.category = CAST(:c AS VARCHAR))
             ORDER BY CASE c.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, c.created_at
             LIMIT :l OFFSET :o""";
}