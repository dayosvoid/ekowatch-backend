CREATE TABLE idempotency_records (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    operation varchar(40) NOT NULL CHECK (operation = 'createReport'),
    idempotency_key uuid NOT NULL,
    request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    report_id uuid NOT NULL REFERENCES reports(id),
    response_payload jsonb NOT NULL CHECK (jsonb_typeof(response_payload) = 'object'),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT uq_idempotency_user_operation_key UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT ck_idempotency_retention CHECK (expires_at = created_at + interval '24 hours')
);
-- Expiry ends replay protection; records are replaced lazily under the same unique key.
-- Existing reports and UUIDs are untouched. No historical requests can be backfilled.
