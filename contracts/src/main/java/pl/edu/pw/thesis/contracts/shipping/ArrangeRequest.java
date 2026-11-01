package pl.edu.pw.thesis.contracts.shipping;

import java.util.Objects;
import java.util.UUID;

/** Body for POST /api/v1/shipments/arrange. Idempotency via {@code Idempotency-Key} header only. */
public record ArrangeRequest(
        UUID orderId
) {
    public ArrangeRequest {
        Objects.requireNonNull(orderId, "orderId");
    }

    public String idempotencyFingerprint() {
        return orderId.toString();
    }
}
