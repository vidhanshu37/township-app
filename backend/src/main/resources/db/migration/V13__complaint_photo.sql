-- The app limits each complaint to 3 photos of at most 2 MB, and checks that the bytes are a real image.
CREATE TABLE complaint_photo
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    complaint_id BIGINT      NOT NULL REFERENCES complaint (id),
    uploaded_by  BIGINT      NOT NULL REFERENCES users (id),
    content_type VARCHAR(20) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    data         BYTEA       NOT NULL CHECK (octet_length(data) BETWEEN 1 AND 2097152),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_complaint_photo ON complaint_photo (complaint_id, id);