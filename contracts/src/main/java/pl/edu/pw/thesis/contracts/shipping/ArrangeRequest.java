package pl.edu.pw.thesis.contracts.shipping;

import java.util.Objects;
import java.util.UUID;

public record ArrangeRequest(
        UUID orderId,
        String idempotencyKey
) {
    public ArrangeRequest {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
