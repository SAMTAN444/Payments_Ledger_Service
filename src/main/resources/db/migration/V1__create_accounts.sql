CREATE TABLE accounts (
    id         UUID PRIMARY KEY,
    owner_name TEXT        NOT NULL,
    currency   CHAR(3)     NOT NULL,
    kind       TEXT        NOT NULL CHECK (kind IN ('CUSTOMER', 'SYSTEM')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
