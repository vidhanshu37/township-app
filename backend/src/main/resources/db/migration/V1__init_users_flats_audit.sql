CREATE TABLE flats (
   id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   flat_no    VARCHAR(20)  NOT NULL UNIQUE,
   tower      VARCHAR(10)  NOT NULL,
   area_sqft  NUMERIC(8,2) NOT NULL CHECK (area_sqft > 0),
   created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
   id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   name          VARCHAR(100) NOT NULL,
   phone         VARCHAR(15)  NOT NULL UNIQUE,
   role          VARCHAR(20)  NOT NULL CHECK (role IN ('RESIDENT', 'HELP_DESK', 'COMMITTEE')),
   resident_type VARCHAR(10)  CHECK (resident_type IN ('OWNER', 'TENANT')),
   flat_id       BIGINT REFERENCES flats(id),
   created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
   CONSTRAINT resident_has_flat CHECK (role <> 'RESIDENT' OR (flat_id IS NOT NULL AND resident_type IS NOT NULL))
);

CREATE INDEX idx_users_flat ON users(flat_id);

CREATE TABLE audit_log (
   id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   actor_id    BIGINT REFERENCES users(id),
   action      VARCHAR(50) NOT NULL,
   entity_type VARCHAR(50) NOT NULL,
   entity_id   VARCHAR(50),
   details     JSONB,
   created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);