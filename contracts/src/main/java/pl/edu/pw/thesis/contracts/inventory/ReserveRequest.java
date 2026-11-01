package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

/** Body for POST /api/v1/reservations. Idempotency via {@code Idempotency-Key} header only. */
public record ReserveRequest(
        UUID orderId,
        String sku,
        int qty
) {
    public ReserveRequest {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
    }

    /** Logical payload fingerprint for idempotency conflict checks. */
    public String idempotencyFingerprint() {
        return orderId + "|" + sku + "|" + qty;
    }
}
