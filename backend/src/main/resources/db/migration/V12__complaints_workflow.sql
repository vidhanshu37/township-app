-- Complaint workflow: duty manager, routing, response timer (SLA), rating on closure, manual category.

-- 1. Staff who can receive complaints. One of them is the duty manager.
ALTER TABLE users
    ADD COLUMN duty_manager BOOLEAN NOT NULL DEFAULT false;

INSERT INTO users (name, phone, role, duty_manager)
VALUES ('Duty Manager Demo', '9000000011', 'HELP_DESK', true),
       ('Maintenance Lead Demo', '9000000012', 'HELP_DESK', false);

-- 2. Which staff member a new complaint of each category goes to.
CREATE TABLE complaint_routing
(
    category VARCHAR(20) PRIMARY KEY
        CHECK (category IN ('PLUMBING', 'ELECTRICAL', 'LIFT', 'SECURITY', 'SAFETY',
                            'HOUSEKEEPING', 'PARKING', 'NOISE', 'OTHER')),
    user_id  BIGINT NOT NULL REFERENCES users (id)
);

INSERT INTO complaint_routing (category, user_id)
SELECT c.category, (SELECT id FROM users WHERE phone = c.phone)
FROM (VALUES ('PLUMBING', '9000000012'),
             ('ELECTRICAL', '9000000012'),
             ('LIFT', '9000000012'),
             ('SAFETY', '9000000011'),
             ('SECURITY', '9000000010'),
             ('HOUSEKEEPING', '9000000010'),
             ('PARKING', '9000000010'),
             ('NOISE', '9000000010'),
             ('OTHER', '9000000010')) AS c (category, phone);

-- 3. New complaint columns.
ALTER TABLE complaint
    ADD COLUMN response_due_at TIMESTAMPTZ,
    ADD COLUMN first_response_at TIMESTAMPTZ,
    ADD COLUMN escalated         BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN rating            SMALLINT CHECK (rating BETWEEN 1 AND 5),
    ADD COLUMN rating_comment    VARCHAR(300),
    ADD COLUMN rated_at          TIMESTAMPTZ;

UPDATE complaint
SET response_due_at = created_at + CASE priority
                                       WHEN 'HIGH' THEN interval '2 hours'
    WHEN 'MEDIUM' THEN interval '24 hours'
    ELSE interval '72 hours'
END,
    escalated         = (triage_source = 'SAFETY_RULE'),
    first_response_at = CASE WHEN status <> 'OPEN' THEN updated_at
END;

ALTER TABLE complaint
    ALTER COLUMN response_due_at SET NOT NULL,
    ADD CONSTRAINT complaint_rating_has_time CHECK (rating IS NULL OR rated_at IS NOT NULL);

-- A help desk member can change the category by hand.
ALTER TABLE complaint DROP CONSTRAINT complaint_triage_source_check;
ALTER TABLE complaint
    ADD CONSTRAINT complaint_triage_source_check
        CHECK (triage_source IN ('SAFETY_RULE', 'KEYWORD', 'FALLBACK', 'LLM', 'MANUAL'));

-- Used by the dashboard for response times and breaches.
CREATE INDEX idx_complaint_response ON complaint (status, response_due_at);

-- 4. Sample data from the brief: T12-0408 has one open complaint, a water leak.
INSERT INTO complaint (flat_id, created_by, text, category, priority, status, triage_source, triage_reason,
                       assigned_to, created_at, updated_at, response_due_at)
VALUES ((SELECT id FROM flats WHERE flat_no = 'T12-0408'),
        (SELECT id FROM users WHERE phone = '9000000002'),
        'Water leak from the bathroom ceiling. It started three days ago and is getting worse.',
        'PLUMBING', 'MEDIUM', 'OPEN', 'KEYWORD', 'keywords: leak, water, bathroom',
        (SELECT id FROM users WHERE phone = '9000000012'),
        now() - interval '3 days', now() - interval '3 days', now() - interval '2 days');

INSERT INTO complaint_event (complaint_id, actor_id, event_type, from_status, to_status, note, created_at)
SELECT id, created_by, 'CREATED', NULL, 'OPEN', triage_reason, created_at
FROM complaint
WHERE created_at < now() - interval '2 days'
  AND NOT EXISTS (SELECT 1 FROM complaint_event e WHERE e.complaint_id = complaint.id);