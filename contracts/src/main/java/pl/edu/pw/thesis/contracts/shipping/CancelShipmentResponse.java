package pl.edu.pw.thesis.contracts.shipping;

import java.util.Objects;
import java.util.UUID;

public record CancelShipmentResponse(
        UUID shipmentId,
        ShipmentState state
) {
    public CancelShipmentResponse {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(state, "state");
    }
}
