-- TH-104 shipping persistence (schema shipping)
-- States: NONE (conceptual initial) → ARRANGED → CANCELLED
-- Rows are created on arrange as ARRANGED (no persisted NONE row).

CREATE TABLE shipments (
    shipment_id UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    state       VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT shipments_state_chk CHECK (state IN ('NONE', 'ARRANGED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_shipments_order ON shipments (order_id);

CREATE TABLE idempotency_records (
    idempotency_key     VARCHAR(256) PRIMARY KEY,
    operation           VARCHAR(32) NOT NULL,
    request_fingerprint TEXT NOT NULL,
    http_status         INTEGER NOT NULL,
    response_body       TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE operation_log (
    id              BIGSERIAL PRIMARY KEY,
    operation       VARCHAR(32) NOT NULL,
    shipment_id     UUID,
    order_id        UUID,
    idempotency_key VARCHAR(256),
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operation_log_shipment ON operation_log (shipment_id);
CREATE INDEX idx_operation_log_order ON operation_log (order_id);
