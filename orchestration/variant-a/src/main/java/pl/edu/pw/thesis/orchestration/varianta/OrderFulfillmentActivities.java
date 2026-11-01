package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ConsumeResponse;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReserveResponse;
import pl.edu.pw.thesis.contracts.payment.ChargeResponse;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.shipping.ArrangeResponse;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;

/** HTTP I/O to shared services. All network calls live here (not in the workflow). */
@ActivityInterface
public interface OrderFulfillmentActivities {

    @ActivityMethod
    ReserveResponse reserve(UUID orderId, String sku, int qty);

    @ActivityMethod
    ChargeResponse charge(UUID orderId, Money amount);

    @ActivityMethod
    ArrangeResponse arrange(UUID orderId);

    @ActivityMethod
    ConsumeResponse consume(UUID reservationId);

    @ActivityMethod
    ReservationSnapshot getReservation(UUID reservationId);

    @ActivityMethod
    PaymentSnapshot getPayment(UUID paymentId);

    @ActivityMethod
    ShipmentSnapshot getShipment(UUID shipmentId);
}
