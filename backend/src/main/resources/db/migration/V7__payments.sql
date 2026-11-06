CREATE TABLE payment
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flat_id            BIGINT         NOT NULL REFERENCES flats (id),
    gateway_order_id   VARCHAR(64)    NOT NULL UNIQUE,
    gateway_payment_id VARCHAR(64) UNIQUE,
    amount             NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status             VARCHAR(10)    NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED', 'SUCCESS', 'FAILED')),
    created_by         BIGINT REFERENCES users (id),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    paid_at            TIMESTAMPTZ
);

CREATE INDEX idx_payment_flat ON payment (flat_id, created_at DESC);