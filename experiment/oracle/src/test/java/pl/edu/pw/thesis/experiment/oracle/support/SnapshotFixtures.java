package pl.edu.pw.thesis.experiment.oracle.support;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.oracle.snapshot.OperationLogEntry;
import pl.edu.pw.thesis.experiment.oracle.snapshot.PaymentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ReservationView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ShipmentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.StockView;

public final class SnapshotFixtures {

    public static final String SKU = "SKU-MVP-1";

    private SnapshotFixtures() {}

    public static BusinessSnapshot snapshot(
            UUID orderId,
            ReservationState reservationState,
            PaymentState paymentState,
            ShipmentState shipmentState,
            List<OperationLogEntry> logs) {
        return snapshot(orderId, reservationState, paymentState, shipmentState, logs, true);
    }

    public static BusinessSnapshot snapshot(
            UUID orderId,
            ReservationState reservationState,
            PaymentState paymentState,
            ShipmentState shipmentState,
            List<OperationLogEntry> logs,
            boolean includeStock) {
        List<ReservationView> reservations = reservationState == null
                ? List.of()
                : List.of(new ReservationView(
                        UUID.randomUUID(), orderId, SKU, 1, reservationState));
        Optional<PaymentView> payment = paymentState == null
                ? Optional.empty()
                : Optional.of(new PaymentView(
                        UUID.randomUUID(), orderId, 2599L, "PLN", paymentState));
        Optional<ShipmentView> shipment = shipmentState == null
                ? Optional.empty()
                : Optional.of(new ShipmentView(UUID.randomUUID(), orderId, shipmentState));

        int held = reservationState == ReservationState.HELD ? 1 : 0;
        int onHand = reservationState == ReservationState.CONSUMED ? 99 : 100;
        Optional<StockView> stock = includeStock
                ? Optional.of(new StockView(SKU, onHand, held, held))
                : Optional.empty();

        return new BusinessSnapshot(
                orderId, SKU, reservations, payment, shipment, stock, logs);
    }

    public static BusinessSnapshot snapshot(
            UUID orderId,
            ReservationState reservationState,
            PaymentState paymentState,
            ShipmentState shipmentState) {
        return snapshot(orderId, reservationState, paymentState, shipmentState, List.of());
    }

    public static OperationLogEntry paymentLog(UUID orderId, String detail) {
        return new OperationLogEntry(
                "payment", 1L, "charge", UUID.randomUUID(), orderId, detail, Instant.now());
    }
}
