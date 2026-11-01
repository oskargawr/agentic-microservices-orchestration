package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

/** Read model for oracle / GET APIs. */
public record ReservationSnapshot(
        UUID reservationId,
        UUID orderId,
        String sku,
        int qty,
        ReservationState state
) {
    public ReservationSnapshot {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(state, "state");
    }
}
