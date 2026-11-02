package pl.edu.pw.thesis.experiment.oracle.snapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Point-in-time durable business state for one orderId (+ sku stock).
 * Built only from DB entities; workflow outcomes are never included.
 */
public record BusinessSnapshot(
        UUID orderId,
        String sku,
        List<ReservationView> reservations,
        Optional<PaymentView> payment,
        Optional<ShipmentView> shipment,
        Optional<StockView> stock,
        List<OperationLogEntry> operationLogs) {

    public BusinessSnapshot {
        reservations = List.copyOf(reservations);
        operationLogs = List.copyOf(operationLogs);
    }

    public Optional<ReservationView> singleReservation() {
        return reservations.size() == 1 ? Optional.of(reservations.getFirst()) : Optional.empty();
    }

    public long heldReservationCount() {
        return reservations.stream().filter(r -> r.state().name().equals("HELD")).count();
    }
}
