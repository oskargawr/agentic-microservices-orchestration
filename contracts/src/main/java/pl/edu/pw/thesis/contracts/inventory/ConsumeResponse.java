package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

public record ConsumeResponse(
        UUID reservationId,
        ReservationState state
) {
    public ConsumeResponse {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(state, "state");
    }
}
