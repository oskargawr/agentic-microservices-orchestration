-- TH-102 inventory persistence (schema inventory)
-- Accounting: on_hand includes reserved; held = reserved qty; available = on_hand - held

CREATE TABLE stock (
    sku         VARCHAR(64) PRIMARY KEY,
    on_hand     INTEGER NOT NULL CHECK (on_hand >= 0),
    held        INTEGER NOT NULL CHECK (held >= 0),
    CONSTRAINT stock_on_hand_covers_held CHECK (on_hand >= held)
);

CREATE TABLE reservations (
    reservation_id UUID PRIMARY KEY,
    order_id       UUID NOT NULL,
    sku            VARCHAR(64) NOT NULL REFERENCES stock (sku),
    qty            INTEGER NOT NULL CHECK (qty > 0),
    state          VARCHAR(16) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reservations_state_chk CHECK (state IN ('HELD', 'RELEASED', 'CONSUMED'))
);

-- I2: at most one active HELD reservation per order
CREATE UNIQUE INDEX uq_reservations_order_held
    ON reservations (order_id)
    WHERE state = 'HELD';

CREATE INDEX idx_reservations_order ON reservations (order_id);
CREATE INDEX idx_reservations_sku_state ON reservations (sku, state);

-- Idempotency store: key unique; fingerprint detects payload conflicts; response replayed
CREATE TABLE idempotency_records (
    idempotency_key    VARCHAR(256) PRIMARY KEY,
    operation          VARCHAR(32) NOT NULL,
    request_fingerprint TEXT NOT NULL,
    http_status        INTEGER NOT NULL,
    response_body      TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Lightweight append-only log for oracle / forbidden-transition forensics (not event sourcing)
CREATE TABLE operation_log (
    id               BIGSERIAL PRIMARY KEY,
    operation        VARCHAR(32) NOT NULL,
    reservation_id   UUID,
    order_id         UUID,
    sku              VARCHAR(64),
    qty              INTEGER,
    idempotency_key  VARCHAR(256),
    detail           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operation_log_reservation ON operation_log (reservation_id);
CREATE INDEX idx_operation_log_order ON operation_log (order_id);

-- Protocol-v0 baseline SKU (EXPERIMENTAL_PROTOCOL_V0 §3)
INSERT INTO stock (sku, on_hand, held) VALUES ('SKU-MVP-1', 100, 0);
