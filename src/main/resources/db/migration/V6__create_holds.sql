CREATE TABLE holds (
    id              UUID        PRIMARY KEY,
    from_account_id UUID        NOT NULL REFERENCES accounts (id),
    to_account_id   UUID        NOT NULL REFERENCES accounts (id),
    amount          BIGINT      NOT NULL CHECK (amount > 0),
    currency        CHAR(3)     NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED')),
    idempotency_key TEXT        NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (from_account_id <> to_account_id)
);

CREATE INDEX holds_active_from_account_idx ON holds (from_account_id)
    WHERE status = 'ACTIVE';
