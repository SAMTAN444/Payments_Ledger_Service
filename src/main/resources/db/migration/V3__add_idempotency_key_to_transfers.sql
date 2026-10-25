ALTER TABLE transfers
    ADD COLUMN idempotency_key TEXT,
    ADD CONSTRAINT transfers_idempotency_key_unique UNIQUE (idempotency_key);