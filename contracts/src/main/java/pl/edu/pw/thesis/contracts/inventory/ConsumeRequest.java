package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

/**
 * Marks a hold as fulfilled: {@code HELD → CONSUMED}.
 * <p><strong>Owner C-101-1:</strong> called by orchestration only after Shipping {@code arrange}
 * has been confirmed successful. Updates {@code held} and {@code onHand} atomically once;
 * replay must not decrement again.
 */
public record ConsumeRequest(
        UUID reservationId
) {
    public ConsumeRequest {
        Objects.requireNonNull(reservationId, "reservationId");
    }

    public String idempotencyFingerprint() {
        return reservationId.toString();
    }
}
