CREATE TABLE complaint
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flat_id       BIGINT        NOT NULL REFERENCES flats (id),
    created_by    BIGINT        NOT NULL REFERENCES users (id),
    text          VARCHAR(1000) NOT NULL,
    category      VARCHAR(20)   NOT NULL
        CHECK (category IN ('PLUMBING', 'ELECTRICAL', 'LIFT', 'SECURITY', 'SAFETY',
                            'HOUSEKEEPING', 'PARKING', 'NOISE', 'OTHER')),
    priority      VARCHAR(10)   NOT NULL CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    status        VARCHAR(12)   NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    triage_source VARCHAR(12)   NOT NULL CHECK (triage_source IN ('SAFETY_RULE', 'KEYWORD', 'FALLBACK', 'LLM')),
    triage_reason VARCHAR(300),
    -- a new complaint that looks like an open one is only LINKED to it, never auto-closed
    duplicate_of  BIGINT REFERENCES complaint (id),
    assigned_to   BIGINT REFERENCES users (id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ,
    CONSTRAINT complaint_not_own_duplicate CHECK (duplicate_of IS NULL OR duplicate_of <> id)
);

CREATE INDEX idx_complaint_queue ON complaint (status, priority, created_at);
CREATE INDEX idx_complaint_flat ON complaint (flat_id, created_at DESC);

CREATE TABLE complaint_event
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    complaint_id BIGINT      NOT NULL REFERENCES complaint (id),
    actor_id     BIGINT REFERENCES users (id),
    event_type   VARCHAR(20) NOT NULL,
    from_status  VARCHAR(12),
    to_status    VARCHAR(12),
    note         VARCHAR(300),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_complaint_event ON complaint_event (complaint_id, created_at);