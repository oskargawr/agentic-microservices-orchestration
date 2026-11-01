package pl.edu.pw.thesis.orchestration.varianta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ConsumeResponse;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.inventory.ReserveResponse;
import pl.edu.pw.thesis.contracts.payment.ChargeResponse;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ArrangeResponse;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

/** Test double that records activity order (optionally delegates to real HTTP activities). */
final class RecordingActivities implements OrderFulfillmentActivities {

    private final List<String> operations = new CopyOnWriteArrayList<>();
    private final OrderFulfillmentActivities delegate;

    RecordingActivities() {
        this(null);
    }

    RecordingActivities(OrderFulfillmentActivities delegate) {
        this.delegate = delegate;
    }

    List<String> operations() {
        return Collections.unmodifiableList(new ArrayList<>(operations));
    }

    @Override
    public ReserveResponse reserve(UUID orderId, String sku, int qty) {
        operations.add("reserve");
        if (delegate != null) {
            return delegate.reserve(orderId, sku, qty);
        }
        UUID reservationId = UUID.randomUUID();
        return new ReserveResponse(reservationId, orderId, sku, qty, ReservationState.HELD);
    }

    @Override
    public ChargeResponse charge(UUID orderId, Money amount) {
        operations.add("charge");
        if (delegate != null) {
            return delegate.charge(orderId, amount);
        }
        return new ChargeResponse(UUID.randomUUID(), orderId, amount, PaymentState.CAPTURED);
    }

    @Override
    public ArrangeResponse arrange(UUID orderId) {
        operations.add("arrange");
        if (delegate != null) {
            return delegate.arrange(orderId);
        }
        return new ArrangeResponse(UUID.randomUUID(), orderId, ShipmentState.ARRANGED);
    }

    @Override
    public ConsumeResponse consume(UUID reservationId) {
        operations.add("consume");
        if (delegate != null) {
            return delegate.consume(reservationId);
        }
        return new ConsumeResponse(reservationId, ReservationState.CONSUMED);
    }

    @Override
    public ReservationSnapshot getReservation(UUID reservationId) {
        operations.add("getReservation");
        if (delegate != null) {
            return delegate.getReservation(reservationId);
        }
        return new ReservationSnapshot(reservationId, UUID.randomUUID(), "SKU-MVP-1", 1, ReservationState.CONSUMED);
    }

    @Override
    public PaymentSnapshot getPayment(UUID paymentId) {
        operations.add("getPayment");
        if (delegate != null) {
            return delegate.getPayment(paymentId);
        }
        return new PaymentSnapshot(paymentId, UUID.randomUUID(), Money.plnMinor(2599), PaymentState.CAPTURED);
    }

    @Override
    public ShipmentSnapshot getShipment(UUID shipmentId) {
        operations.add("getShipment");
        if (delegate != null) {
            return delegate.getShipment(shipmentId);
        }
        return new ShipmentSnapshot(shipmentId, UUID.randomUUID(), ShipmentState.ARRANGED);
    }
}
