package pl.edu.pw.thesis.contracts.shipping;

import java.util.Objects;
import java.util.UUID;

/** Logical cancel intent. Idempotency via {@code Idempotency-Key} header only. */
public record CancelShipmentRequest(
        UUID shipmentId
) {
    public CancelShipmentRequest {
        Objects.requireNonNull(shipmentId, "shipmentId");
    }

    public String idempotencyFingerprint() {
        return shipmentId.toString();
    }
}
