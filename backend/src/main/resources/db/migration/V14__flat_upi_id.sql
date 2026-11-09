CREATE TABLE flat_upi_id
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flat_id    BIGINT      NOT NULL REFERENCES flats (id),
    upi_id     VARCHAR(80) NOT NULL
        CHECK (upi_id = lower(upi_id) AND upi_id ~ '^[a-z0-9][a-z0-9._-]+@[a-z][a-z0-9]+$'),
    source     VARCHAR(10) NOT NULL CHECK (source IN ('SEED', 'LEARNED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (flat_id, upi_id)
);

INSERT INTO flat_upi_id (flat_id, upi_id, source)
VALUES ((SELECT id FROM flats WHERE flat_no = 'T07-1203'), 'neeraj.kulkarni@okhdfcbank', 'SEED'),
       ((SELECT id FROM flats WHERE flat_no = 'T12-0408'), 'swati.joshi@oksbi', 'SEED'),
       ((SELECT id FROM flats WHERE flat_no = 'T21-1601'), 'aamir.shaikh@ybl', 'SEED');