# Inventory concurrency & idempotency (TH-102)

## Accounting

| Field | Meaning |
| --- | --- |
| `on_hand` | Physical stock including reserved |
| `held` | Quantity currently in `HELD` reservations |
| `available` | `on_hand - held` |

- **Reserve:** `held += qty` (require `available >= qty`); `on_hand` unchanged  
- **Release:** `held -= qty`; state → `RELEASED`  
- **Consume:** `held -= qty` and `on_hand -= qty` atomically; state → `CONSUMED`

## Transaction strategy

Each mutating HTTP call is **one Spring `@Transactional` boundary** covering:

1. `pg_advisory_xact_lock(hashtext(idempotency_key))` — serialize same-key concurrent callers  
2. Read `idempotency_records` (`FOR UPDATE`) — replay or conflict  
3. Lock `stock` and/or `reservations` with `SELECT … FOR UPDATE`  
4. Apply stock + reservation mutations  
5. Insert idempotency response + append `operation_log`  
6. Commit

Why safe:

- Same idempotency key cannot run business logic twice (advisory lock + unique key).  
- Oversell prevented by row lock on `stock` before checking `available`.  
- Concurrent release vs consume on one reservation serialized by `FOR UPDATE` on that row; exactly one terminal transition succeeds.  
- Partial unique index: at most one `HELD` reservation per `order_id`.

## Oracle

Future oracle (TH-401) can read tables `stock`, `reservations`, `operation_log` directly in schema `inventory` — no dependency on Java service logic.
