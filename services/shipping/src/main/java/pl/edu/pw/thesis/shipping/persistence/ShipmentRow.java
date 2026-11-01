package pl.edu.pw.thesis.shipping.persistence;

import java.util.UUID;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

public record ShipmentRow(
        UUID shipmentId,
        UUID orderId,
        ShipmentState state
) {
}
