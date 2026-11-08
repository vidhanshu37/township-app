ALTER TABLE flats
    ADD COLUMN pay_ref VARCHAR(12) GENERATED ALWAYS AS ('TWN' || lpad(id::text, 4, '0')) STORED;
ALTER TABLE flats
    ADD CONSTRAINT uq_flats_pay_ref UNIQUE (pay_ref);

CREATE TABLE bank_statement_file
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    filename       VARCHAR(255) NOT NULL,
    sha256         CHAR(64)     NOT NULL UNIQUE,
    content        BYTEA        NOT NULL,
    uploaded_by    BIGINT REFERENCES users (id),
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    total_rows     INT          NOT NULL DEFAULT 0,
    credits        INT          NOT NULL DEFAULT 0,
    debits_ignored INT          NOT NULL DEFAULT 0,
    duplicates     INT          NOT NULL DEFAULT 0,
    auto_matched   INT          NOT NULL DEFAULT 0,
    needs_review   INT          NOT NULL DEFAULT 0,
    unmatched      INT          NOT NULL DEFAULT 0,
    errors         INT          NOT NULL DEFAULT 0
);

CREATE TABLE bank_txn
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_id     BIGINT         NOT NULL REFERENCES bank_statement_file (id),
    line_no     INT            NOT NULL,
    txn_date    DATE           NOT NULL,
    narration   TEXT           NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    utr         VARCHAR(60),
    fingerprint CHAR(64)       NOT NULL UNIQUE,
    status      VARCHAR(15)    NOT NULL
        CHECK (status IN ('AUTO_MATCHED', 'NEEDS_REVIEW', 'UNMATCHED', 'CONFIRMED', 'REJECTED')),
    flat_id     BIGINT REFERENCES flats (id),
    score       INT            NOT NULL,
    reasons     TEXT,
    note        TEXT,
    payment_id  BIGINT REFERENCES payment (id),
    resolved_by BIGINT REFERENCES users (id),
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- a bank reference (UTR) is unique in the real world
CREATE UNIQUE INDEX uq_bank_txn_utr ON bank_txn (utr) WHERE utr IS NOT NULL;
CREATE INDEX idx_bank_txn_status ON bank_txn (status, created_at DESC);
CREATE INDEX idx_bank_txn_file ON bank_txn (file_id);