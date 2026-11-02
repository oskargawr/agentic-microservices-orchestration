package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.failure.ApplicationFailure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ConsumeResponse;
import pl.edu.pw.thesis.contracts.inventory.ReleaseResponse;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.inventory.ReserveResponse;
import pl.edu.pw.thesis.contracts.payment.ChargeResponse;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.payment.RefundResponse;
import pl.edu.pw.thesis.contracts.shipping.ArrangeResponse;
import pl.edu.pw.thesis.contracts.shipping.CancelShipmentResponse;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

/** Test double that records activity order (optionally delegates to real HTTP activities). */
final class RecordingActivities implements OrderFulfillmentActivities {

    private final List<String> operations = new CopyOnWriteArrayList<>();
    private final OrderFulfillmentActivities delegate;

    private PaymentState forcedChargeState;
    private Supplier<ArrangeResponse> arrangeOverride;
    private Supplier<ConsumeResponse> consumeOverride;
    private Supplier<RefundResponse> refundOverride;
    private Supplier<PaymentSnapshot> resolveOverride;
    private int resolveCalls;
    /** Remaining retryable charge failures before a normal success (TH-202). */
    private int chargeRetryableFailuresRemaining;
    private int arrangeInvocations;
    private int chargeInvocations;

    private UUID lastOrderId;
    private UUID lastReservationId;
    private UUID lastPaymentId;
    private UUID lastShipmentId;
    private PaymentState paymentState = PaymentState.CAPTURED;
    private ReservationState reservationState = ReservationState.HELD;
    private ShipmentState shipmentState = ShipmentState.ARRANGED;

    RecordingActivities() {
        this(null);
    }

    RecordingActivities(OrderFulfillmentActivities delegate) {
        this.delegate = delegate;
    }

    List<String> operations() {
        return Collections.unmodifiableList(new ArrayList<>(operations));
    }

    void failArrangeWithBusiness(String message) {
        arrangeOverride = () -> {
            throw ApplicationFailure.newNonRetryableFailure(message, ActivityPolicies.BUSINESS_FAILURE_TYPE);
        };
    }

    /** Fail arrange with retryable {@code HTTP_503} until Temporal exhausts retries (TH-202-4). */
    void failArrangeAlwaysRetryable(String message) {
        arrangeOverride = () -> {
            throw ApplicationFailure.newFailure(message, "HTTP_503");
        };
    }

    /**
     * Fail arrange with retryable {@code HTTP_503} for the first {@code failuresBeforeSuccess}
     * invocations, then succeed (TH-202-2).
     */
    void failArrangeRetryableThenSucceed(int failuresBeforeSuccess, String message) {
        arrangeOverride = () -> {
            if (arrangeInvocations <= failuresBeforeSuccess) {
                throw ApplicationFailure.newFailure(message, "HTTP_503");
            }
            lastShipmentId = UUID.randomUUID();
            shipmentState = ShipmentState.ARRANGED;
            return new ArrangeResponse(lastShipmentId, lastOrderId, ShipmentState.ARRANGED);
        };
    }

    void failConsumeWithBusiness(String message) {
        consumeOverride = () -> {
            throw ApplicationFailure.newNonRetryableFailure(message, ActivityPolicies.BUSINESS_FAILURE_TYPE);
        };
    }

    void failRefundWithBusiness(String message) {
        refundOverride = () -> {
            throw ApplicationFailure.newNonRetryableFailure(message, ActivityPolicies.BUSINESS_FAILURE_TYPE);
        };
    }

    void chargeReturns(PaymentState state) {
        this.forcedChargeState = state;
    }

    /** Throw retryable charge failures this many times, then succeed (TH-202-2). */
    void failChargeRetryableTimes(int times) {
        this.chargeRetryableFailuresRemaining = times;
    }

    int arrangeInvocations() {
        return arrangeInvocations;
    }

    int chargeInvocations() {
        return chargeInvocations;
    }

    int resolveInvocations() {
        return (int) operations.stream().filter("resolve"::equals).count();
    }

    void resolveSequence(PaymentState... states) {
        resolveCalls = 0;
        PaymentState[] copy = states.clone();
        resolveOverride = () -> {
            PaymentState next = copy[Math.min(resolveCalls, copy.length - 1)];
            resolveCalls++;
            paymentState = next;
            return new PaymentSnapshot(lastPaymentId, UUID.randomUUID(), Money.plnMinor(2599), next);
        };
    }

    @Override
    public ReserveResponse reserve(UUID orderId, String sku, int qty) {
        operations.add("reserve");
        lastOrderId = orderId;
        if (delegate != null) {
            ReserveResponse r = delegate.reserve(orderId, sku, qty);
            lastReservationId = r.reservationId();
            reservationState = r.state();
            return r;
        }
        lastReservationId = UUID.randomUUID();
        reservationState = ReservationState.HELD;
        return new ReserveResponse(lastReservationId, orderId, sku, qty, ReservationState.HELD);
    }

    @Override
    public ChargeResponse charge(UUID orderId, Money amount, String faultHint) {
        operations.add("charge");
        chargeInvocations++;
        if (chargeRetryableFailuresRemaining > 0) {
            chargeRetryableFailuresRemaining--;
            throw ApplicationFailure.newFailure("transient charge", "HTTP_503");
        }
        if (forcedChargeState != null) {
            lastPaymentId = UUID.randomUUID();
            paymentState = forcedChargeState;
            return new ChargeResponse(lastPaymentId, orderId, amount, forcedChargeState);
        }
        if (delegate != null) {
            ChargeResponse r = delegate.charge(orderId, amount, faultHint);
            lastPaymentId = r.paymentId();
            paymentState = r.state();
            return r;
        }
        lastPaymentId = UUID.randomUUID();
        paymentState = PaymentState.CAPTURED;
        return new ChargeResponse(lastPaymentId, orderId, amount, PaymentState.CAPTURED);
    }

    @Override
    public ArrangeResponse arrange(UUID orderId) {
        operations.add("arrange");
        lastOrderId = orderId;
        arrangeInvocations++;
        if (arrangeOverride != null) {
            return arrangeOverride.get();
        }
        if (delegate != null) {
            ArrangeResponse r = delegate.arrange(orderId);
            lastShipmentId = r.shipmentId();
            shipmentState = r.state();
            return r;
        }
        lastShipmentId = UUID.randomUUID();
        shipmentState = ShipmentState.ARRANGED;
        return new ArrangeResponse(lastShipmentId, orderId, ShipmentState.ARRANGED);
    }

    @Override
    public ConsumeResponse consume(UUID reservationId) {
        operations.add("consume");
        if (consumeOverride != null) {
            return consumeOverride.get();
        }
        if (delegate != null) {
            ConsumeResponse r = delegate.consume(reservationId);
            reservationState = r.state();
            return r;
        }
        reservationState = ReservationState.CONSUMED;
        return new ConsumeResponse(reservationId, ReservationState.CONSUMED);
    }

    @Override
    public ReleaseResponse release(UUID reservationId) {
        operations.add("release");
        if (delegate != null) {
            ReleaseResponse r = delegate.release(reservationId);
            reservationState = r.state();
            return r;
        }
        reservationState = ReservationState.RELEASED;
        return new ReleaseResponse(reservationId, ReservationState.RELEASED);
    }

    @Override
    public RefundResponse refund(UUID paymentId) {
        operations.add("refund");
        if (refundOverride != null) {
            return refundOverride.get();
        }
        if (delegate != null) {
            RefundResponse r = delegate.refund(paymentId);
            paymentState = r.state();
            return r;
        }
        paymentState = PaymentState.REFUNDED;
        return new RefundResponse(paymentId, PaymentState.REFUNDED);
    }

    @Override
    public CancelShipmentResponse cancelShipment(UUID shipmentId) {
        operations.add("cancel");
        if (delegate != null) {
            CancelShipmentResponse r = delegate.cancelShipment(shipmentId);
            shipmentState = r.state();
            return r;
        }
        shipmentState = ShipmentState.CANCELLED;
        return new CancelShipmentResponse(shipmentId, ShipmentState.CANCELLED);
    }

    @Override
    public PaymentSnapshot resolvePayment(UUID paymentId, String faultHint) {
        operations.add("resolve");
        if (resolveOverride != null) {
            return resolveOverride.get();
        }
        if (delegate != null) {
            PaymentSnapshot r = delegate.resolvePayment(paymentId, faultHint);
            paymentState = r.state();
            return r;
        }
        return new PaymentSnapshot(paymentId, UUID.randomUUID(), Money.plnMinor(2599), paymentState);
    }

    @Override
    public ReservationSnapshot getReservation(UUID reservationId) {
        operations.add("getReservation");
        if (delegate != null) {
            ReservationSnapshot r = delegate.getReservation(reservationId);
            reservationState = r.state();
            return r;
        }
        return new ReservationSnapshot(
                reservationId, UUID.randomUUID(), "SKU-MVP-1", 1, reservationState);
    }

    @Override
    public PaymentSnapshot getPayment(UUID paymentId) {
        operations.add("getPayment");
        if (delegate != null) {
            PaymentSnapshot r = delegate.getPayment(paymentId);
            paymentState = r.state();
            return r;
        }
        return new PaymentSnapshot(paymentId, UUID.randomUUID(), Money.plnMinor(2599), paymentState);
    }

    @Override
    public ShipmentSnapshot getShipment(UUID shipmentId) {
        operations.add("getShipment");
        if (delegate != null) {
            ShipmentSnapshot r = delegate.getShipment(shipmentId);
            shipmentState = r.state();
            return r;
        }
        return new ShipmentSnapshot(shipmentId, UUID.randomUUID(), shipmentState);
    }
}
