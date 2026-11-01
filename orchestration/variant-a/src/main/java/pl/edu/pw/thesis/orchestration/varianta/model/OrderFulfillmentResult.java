package pl.edu.pw.thesis.orchestration.varianta.model;

import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

/**
 * Structured successful result from Variant A happy path.
 * Callers must still verify independent service snapshots (oracle).
 */
public record OrderFulfillmentResult(
        UUID orderId,
        UUID reservationId,
        ReservationState reservationState,
        UUID paymentId,
        PaymentState paymentState,
        UUID shipmentId,
        ShipmentState shipmentState
) {
    public OrderFulfillmentResult {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(reservationState, "reservationState");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(paymentState, "paymentState");
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(shipmentState, "shipmentState");
    }

    public boolean matchesConfirmedSuccessSnapshot() {
        return reservationState == ReservationState.CONSUMED
                && paymentState == PaymentState.CAPTURED
                && shipmentState == ShipmentState.ARRANGED;
    }
}
