-- TH-103 payment persistence (schema payment)
-- Business state in payments.state is oracle ground truth.
-- F3 may return UNKNOWN on charge while persisting CAPTURED; see operation_log.

CREATE TABLE payments (
    payment_id   UUID PRIMARY KEY,
    order_id     UUID NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency     VARCHAR(16) NOT NULL,
    state        VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT payments_state_chk CHECK (
        state IN ('PENDING', 'CAPTURED', 'FAILED', 'UNKNOWN', 'REFUNDED')
    )
);

-- One payment row per order for MVP (charge idempotent by order)
CREATE UNIQUE INDEX uq_payments_order ON payments (order_id);

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
    payment_id      UUID,
    order_id        UUID,
    idempotency_key VARCHAR(256),
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operation_log_payment ON operation_log (payment_id);
CREATE INDEX idx_operation_log_order ON operation_log (order_id);
