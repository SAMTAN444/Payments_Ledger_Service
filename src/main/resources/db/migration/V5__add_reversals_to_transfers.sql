ALTER TABLE transfers
    ADD COLUMN reverses_transfer_id UUID REFERENCES transfers (id),
    ADD CONSTRAINT transfers_not_self_reversal CHECK (reverses_transfer_id <> id);

CREATE INDEX transfers_reverses_transfer_id_idx ON transfers (reverses_transfer_id);
