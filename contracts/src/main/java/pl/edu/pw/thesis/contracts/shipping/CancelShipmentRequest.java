package pl.edu.pw.thesis.contracts.shipping;

import java.util.Objects;
import java.util.UUID;

public record CancelShipmentRequest(
        UUID shipmentId,
        String idempotencyKey
) {
    public CancelShipmentRequest {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
