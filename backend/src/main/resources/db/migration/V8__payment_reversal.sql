ALTER TABLE payment DROP CONSTRAINT payment_status_check;
ALTER TABLE payment ADD CONSTRAINT payment_status_check
    CHECK (status IN ('CREATED', 'SUCCESS', 'FAILED', 'REVERSED'));

CREATE UNIQUE INDEX uq_ledger_one_reversal
    ON ledger_entry (reverses_entry_id) WHERE entry_type = 'REVERSAL';