CREATE TABLE otp_challenge
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone      VARCHAR(15) NOT NULL,
    otp_hash   VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts   INT         NOT NULL DEFAULT 0,
    consumed   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_phone_created ON otp_challenge (phone, created_at DESC);