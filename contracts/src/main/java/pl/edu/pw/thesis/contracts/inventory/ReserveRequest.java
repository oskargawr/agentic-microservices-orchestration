package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

public record ReserveRequest(
        UUID orderId,
        String sku,
        int qty,
        String idempotencyKey
) {
    public ReserveRequest {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
