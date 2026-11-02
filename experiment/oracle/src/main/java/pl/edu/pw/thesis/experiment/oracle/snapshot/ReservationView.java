package pl.edu.pw.thesis.experiment.oracle.snapshot;

import java.util.UUID;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;

public record ReservationView(
        UUID reservationId, UUID orderId, String sku, int qty, ReservationState state) {}
