package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ConsumeResponse;
import pl.edu.pw.thesis.contracts.inventory.ReleaseResponse;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReserveResponse;
import pl.edu.pw.thesis.contracts.payment.ChargeResponse;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.payment.RefundResponse;
import pl.edu.pw.thesis.contracts.shipping.ArrangeResponse;
import pl.edu.pw.thesis.contracts.shipping.CancelShipmentResponse;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;

/** HTTP I/O to shared services. All network calls live here (not in the workflow). */
@ActivityInterface
public interface OrderFulfillmentActivities {

    @ActivityMethod
    ReserveResponse reserve(UUID orderId, String sku, int qty);

    /** {@code faultHint} maps to {@code X-Thesis-Fault} when non-null/blank; otherwise omitted. */
    @ActivityMethod
    ChargeResponse charge(UUID orderId, Money amount, String faultHint);

    /** {@code faultHint} maps to shipping {@code X-Thesis-Fault} when non-null/blank; otherwise omitted. */
    @ActivityMethod
    ArrangeResponse arrange(UUID orderId, String faultHint);

    @ActivityMethod
    ConsumeResponse consume(UUID reservationId);

    @ActivityMethod
    ReleaseResponse release(UUID reservationId);

    @ActivityMethod
    RefundResponse refund(UUID paymentId);

    @ActivityMethod
    CancelShipmentResponse cancelShipment(UUID shipmentId);

    /** {@code faultHint} optional resolve probe control ({@code X-Thesis-Fault}). */
    @ActivityMethod
    PaymentSnapshot resolvePayment(UUID paymentId, String faultHint);

    @ActivityMethod
    ReservationSnapshot getReservation(UUID reservationId);

    /**
     * {@code faultHint} maps to payment {@code X-Thesis-Fault} when non-null/blank (F3-Unresolved
     * opacity on GET). Pass {@code null} for normal reads (compensation / refresh).
     */
    @ActivityMethod
    PaymentSnapshot getPayment(UUID paymentId, String faultHint);

    @ActivityMethod
    ShipmentSnapshot getShipment(UUID shipmentId);
}
