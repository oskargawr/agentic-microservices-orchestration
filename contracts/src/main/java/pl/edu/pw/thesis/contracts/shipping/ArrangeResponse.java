package pl.edu.pw.thesis.contracts.shipping;

import java.util.Objects;
import java.util.UUID;

public record ArrangeResponse(
        UUID shipmentId,
        UUID orderId,
        ShipmentState state
) {
    public ArrangeResponse {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(state, "state");
    }
}
