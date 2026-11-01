package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

/**
 * Logical release intent. HTTP uses path {@code reservationId}; body optional.
 * Idempotency via {@code Idempotency-Key} header only.
 */
public record ReleaseRequest(
        UUID reservationId
) {
    public ReleaseRequest {
        Objects.requireNonNull(reservationId, "reservationId");
    }

    public String idempotencyFingerprint() {
        return reservationId.toString();
    }
}
