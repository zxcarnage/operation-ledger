CREATE TABLE operations (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL UNIQUE,
    account_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL CHECK (amount > 0),
    currency varchar(3) NOT NULL CHECK (currency IN ('EUR','USD','MDL','RUB')),
    description varchar(250) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('NEW','PROCESSING','COMPLETED','FAILED','CANCELLED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE status_history (
    id bigserial PRIMARY KEY,
    operation_id uuid NOT NULL REFERENCES operations(id),
    from_status varchar(20),
    to_status varchar(20) NOT NULL,
    reason varchar(250) NOT NULL,
    changed_at timestamptz NOT NULL
);
CREATE INDEX ix_status_history_operation ON status_history(operation_id, id);
CREATE TABLE processed_messages (
    event_id uuid PRIMARY KEY,
    payload_hash varchar(64) NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE outbox (
    event_id uuid PRIMARY KEY,
    operation_id uuid NOT NULL REFERENCES operations(id),
    payload text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    attempts integer NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_pending ON outbox(created_at, event_id) WHERE published_at IS NULL;
