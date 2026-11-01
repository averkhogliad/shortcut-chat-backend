CREATE TABLE message_outbox (
    id uuid NOT NULL,
    topic varchar NOT NULL,
    key varchar,
    body jsonb NOT NULL,
    headers jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    state varchar NOT NULL,
    lease_owner varchar,
    lease_until timestamptz,
    retry_count int NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    CONSTRAINT message_outbox_pkey PRIMARY KEY (id)
);
