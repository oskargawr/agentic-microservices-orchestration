package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

public record ReleaseRequest(
        UUID reservationId,
        String idempotencyKey
) {
    public ReleaseRequest {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
