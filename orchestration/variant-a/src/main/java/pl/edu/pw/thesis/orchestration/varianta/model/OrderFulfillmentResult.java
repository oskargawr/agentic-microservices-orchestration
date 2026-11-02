package pl.edu.pw.thesis.orchestration.varianta.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

/**
 * Structured Variant A workflow result (U-201-5).
 * Callers must still verify independent service snapshots (oracle). Temporal COMPLETED ≠ business success.
 */
public record OrderFulfillmentResult(
        UUID orderId,
        BusinessOutcome outcome,
        UUID reservationId,
        ReservationState reservationState,
        UUID paymentId,
        PaymentState paymentState,
        UUID shipmentId,
        ShipmentState shipmentState,
        List<String> compensationLog
) {
    public OrderFulfillmentResult {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(compensationLog, "compensationLog");
        compensationLog = List.copyOf(compensationLog);
    }

    public boolean matchesConfirmedSuccessSnapshot() {
        return outcome == BusinessOutcome.SUCCESS
                && reservationState == ReservationState.CONSUMED
                && paymentState == PaymentState.CAPTURED
                && shipmentState == ShipmentState.ARRANGED;
    }
}
