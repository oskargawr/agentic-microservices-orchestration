package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;
import java.util.UUID;

public record ReleaseResponse(
        UUID reservationId,
        ReservationState state
) {
    public ReleaseResponse {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(state, "state");
    }
}
