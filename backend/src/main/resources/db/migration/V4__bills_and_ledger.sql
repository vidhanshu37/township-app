CREATE TABLE bills
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flat_id    BIGINT         NOT NULL REFERENCES flats (id),
    period DATE NOT NULL CHECK (EXTRACT (DAY FROM period) = 1),
    amount     NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    due_date   DATE           NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_bill_flat_period UNIQUE (flat_id, period)
);

CREATE TABLE ledger_entry
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flat_id           BIGINT         NOT NULL REFERENCES flats (id),
    bill_id           BIGINT REFERENCES bills (id),
    entry_type        VARCHAR(20)    NOT NULL
        CHECK (entry_type IN ('BILL', 'PAYMENT', 'LATE_FEE', 'REVERSAL', 'ADVANCE')),
    amount            NUMERIC(12, 2) NOT NULL,
    reference         VARCHAR(100),
    reverses_entry_id BIGINT REFERENCES ledger_entry (id),
    created_by        BIGINT REFERENCES users (id),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ledger_sign_matches_type CHECK (
        (entry_type IN ('BILL', 'LATE_FEE') AND amount > 0) OR
        (entry_type IN ('PAYMENT', 'ADVANCE') AND amount < 0) OR
        (entry_type = 'REVERSAL' AND amount <> 0)
        )
);

CREATE INDEX idx_ledger_flat ON ledger_entry(flat_id, created_at);
CREATE INDEX idx_ledger_bill ON ledger_entry(bill_id);

-- for single bill entry
CREATE UNIQUE INDEX uq_ledger_one_bill_entry
    ON ledger_entry(bill_id) WHERE entry_type = 'BILL';

-- ledger append only trigger
CREATE FUNCTION forbid_ledger_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entry is append-only; post a REVERSAL entry instead';
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER ledger_entry_append_only
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();