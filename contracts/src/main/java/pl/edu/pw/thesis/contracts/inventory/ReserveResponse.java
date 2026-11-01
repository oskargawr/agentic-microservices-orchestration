package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

public record ReserveResponse(
        UUID reservationId,
        UUID orderId,
        String sku,
        int qty,
        ReservationState state
) {
    public ReserveResponse {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(state, "state");
    }
}
