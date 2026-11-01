package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

/**
 * Marks a hold as fulfilled: {@code HELD → CONSUMED}.
 * Called by orchestration after successful arrange (services stay decoupled).
 */
public record ConsumeRequest(
        UUID reservationId,
        String idempotencyKey
) {
    public ConsumeRequest {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
