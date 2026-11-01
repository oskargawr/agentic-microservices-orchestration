package pl.edu.pw.thesis.inventory.persistence;

import java.util.UUID;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;

public record ReservationRow(
        UUID reservationId,
        UUID orderId,
        String sku,
        int qty,
        ReservationState state
) {
}
