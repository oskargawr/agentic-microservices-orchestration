package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import pl.edu.pw.thesis.orchestration.varianta.model.BusinessOutcome;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

/**
 * Deterministic Variant A saga: reserve → charge → (resolve UNKNOWN) → arrange → consume,
 * with compensation cancel → refund(CAPTURED) → release(HELD) (D-201).
 *
 * <p>TH-208 / D-208: before compensation, bounded idempotent re-POST recovery for missing create
 * ids; honest terminal outcomes (never false {@code COMPENSATED_FAILURE}).
 */
public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    private final OrderFulfillmentActivities activities =
            Workflow.newActivityStub(OrderFulfillmentActivities.class, ActivityPolicies.defaultOptions());
    private final OrderFulfillmentActivities compensationActivities =
            Workflow.newActivityStub(OrderFulfillmentActivities.class, ActivityPolicies.compensationOptions());
    /** D-208 B: exactly one attempt per missing create id (no nested Temporal retry storm). */
    private final OrderFulfillmentActivities recoveryActivities =
            Workflow.newActivityStub(OrderFulfillmentActivities.class, lostIdRecoveryOptions());

    private static ActivityOptions lostIdRecoveryOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setScheduleToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(1)
                        .setDoNotRetry(
                                ActivityPolicies.BUSINESS_FAILURE_TYPE,
                                ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE)
                        .build())
                .build();
    }

    @Override
    public OrderFulfillmentResult fulfill(OrderFulfillmentInput input) {
        SagaState state = new SagaState(input);

        try {
            CancellationScope forward = Workflow.newCancellationScope(() -> runForward(input, state));
            forward.run();
            return state.successResult();
        } catch (ActivityFailure | ApplicationFailure e) {
            return finishWithCompensation(state, e);
        } catch (CanceledFailure e) {
            // Main scope cancelled — still attempt compensation in a detached scope, then rethrow.
            finishWithCompensation(state, e);
            throw e;
        }
    }

    private void runForward(OrderFulfillmentInput input, SagaState state) {
        state.reserveAttempted = true;
        ReserveResponse reserved = activities.reserve(input.orderId(), input.sku(), input.qty());
        require(reserved.state() == ReservationState.HELD,
                "reserve expected HELD, got " + reserved.state());
        state.reservationId = reserved.reservationId();
        state.reservationState = reserved.state();

        state.chargeAttempted = true;
        ChargeResponse charged = activities.charge(input.orderId(), input.amount(), input.chargeFaultHint());
        state.paymentId = charged.paymentId();
        state.paymentState = charged.state();

        if (state.paymentState == PaymentState.UNKNOWN || state.paymentState == PaymentState.PENDING) {
            handleUnresolvedPayment(input, state);
            return;
        }
        if (state.paymentState == PaymentState.FAILED) {
            state.pendingOutcome = BusinessOutcome.COMPENSATED_FAILURE;
            state.forwardFailure = "charge returned FAILED";
            throw ApplicationFailure.newNonRetryableFailure(
                    state.forwardFailure, ActivityPolicies.BUSINESS_FAILURE_TYPE);
        }
        require(state.paymentState == PaymentState.CAPTURED,
                "charge expected CAPTURED, got " + state.paymentState);

        state.arrangeAttempted = true;
        ArrangeResponse arranged = activities.arrange(input.orderId(), input.arrangeFaultHint());
        require(arranged.state() == ShipmentState.ARRANGED,
                "arrange expected ARRANGED, got " + arranged.state());
        state.shipmentId = arranged.shipmentId();
        state.shipmentState = arranged.state();

        // C-101-1: consume only after confirmed arrange — reconcile before compensate on failure
        ConsumeResponse consumed = activities.consume(reserved.reservationId());
        require(consumed.state() == ReservationState.CONSUMED,
                "consume expected CONSUMED, got " + consumed.state());
        state.reservationState = consumed.state();
        state.pendingOutcome = BusinessOutcome.SUCCESS;
    }

    private void handleUnresolvedPayment(OrderFulfillmentInput input, SagaState state) {
        PaymentSnapshot snap = null;
        for (int i = 0; i < input.resolveAttemptLimit(); i++) {
            try {
                snap = activities.resolvePayment(state.paymentId, input.resolveFaultHint());
                state.paymentState = snap.state();
                if (state.paymentState == PaymentState.CAPTURED
                        || state.paymentState == PaymentState.FAILED) {
                    break;
                }
            } catch (ActivityFailure e) {
                // Only exhausted HTTP_503 under explicit transport-opacity hint may continue as
                // unresolved (F3-Unresolved). BUSINESS / other failures must propagate.
                if (!isExhaustedTransportOpacityFailure(e, input.resolveFaultHint())) {
                    throw e;
                }
                snap = null;
            }
        }
        if (state.paymentState != PaymentState.CAPTURED
                && state.paymentState != PaymentState.FAILED) {
            // Fallback GET must carry the same resolve hint (F3-Unresolved: no unhinted escape).
            try {
                snap = activities.getPayment(state.paymentId, input.resolveFaultHint());
                state.paymentState = snap.state();
            } catch (ActivityFailure e) {
                if (!isExhaustedTransportOpacityFailure(e, input.resolveFaultHint())) {
                    throw e;
                }
                snap = null;
            }
        }

        if (state.paymentState == PaymentState.CAPTURED) {
            state.arrangeAttempted = true;
            ArrangeResponse arranged = activities.arrange(input.orderId(), input.arrangeFaultHint());
            require(arranged.state() == ShipmentState.ARRANGED,
                    "arrange expected ARRANGED, got " + arranged.state());
            state.shipmentId = arranged.shipmentId();
            state.shipmentState = arranged.state();

            ConsumeResponse consumed = activities.consume(state.reservationId);
            require(consumed.state() == ReservationState.CONSUMED,
                    "consume expected CONSUMED, got " + consumed.state());
            state.reservationState = consumed.state();
            state.pendingOutcome = BusinessOutcome.SUCCESS;
            return;
        }

        if (state.paymentState == PaymentState.FAILED) {
            state.pendingOutcome = BusinessOutcome.COMPENSATED_FAILURE;
            state.forwardFailure = "payment resolved to FAILED";
            throw ApplicationFailure.newNonRetryableFailure(
                    state.forwardFailure, ActivityPolicies.BUSINESS_FAILURE_TYPE);
        }

        // Still UNKNOWN/PENDING after bounded resolve — AMBIGUOUS path (no arrange, no refund)
        state.pendingOutcome = BusinessOutcome.AMBIGUOUS;
        state.forwardFailure = "payment remains " + state.paymentState + " after bounded resolve";
        throw ApplicationFailure.newNonRetryableFailure(
                state.forwardFailure, ActivityPolicies.BUSINESS_FAILURE_TYPE);
    }

    private OrderFulfillmentResult finishWithCompensation(SagaState state, Exception cause) {
        List<String> log = new ArrayList<>();
        final boolean[] incomplete = {false};

        CancellationScope detached = Workflow.newDetachedCancellationScope(() -> {
            try {
                // D-208 B: recover only after technical activity failure (not BUSINESS rejects).
                if (shouldAttemptLostIdRecovery(cause)) {
                    state.lostIdRecoveryAttempted = true;
                    recoverMissingCreateIds(state, log);
                }
                runCompensation(state, log);
                assertNoOutstandingCompensationRequired(state, log);
            } catch (RuntimeException ex) {
                incomplete[0] = true;
                log.add("compensation-incomplete:" + ex.getMessage());
                throw ex;
            }
        });

        try {
            detached.run();
        } catch (RuntimeException ex) {
            incomplete[0] = true;
            if (log.stream().noneMatch(s -> s.startsWith("compensation-incomplete:"))) {
                log.add("compensation-incomplete:" + ex.getMessage());
            }
            throw ApplicationFailure.newNonRetryableFailure(
                    "Compensation incomplete after forward failure (" + cause.getMessage() + "): "
                            + ex.getMessage() + "; log=" + log,
                    ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE);
        }

        if (incomplete[0]) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Compensation incomplete; log=" + log,
                    ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE);
        }

        refreshSnapshots(state);

        BusinessOutcome outcome = resolveHonestOutcome(state);

        return new OrderFulfillmentResult(
                state.orderId,
                outcome,
                state.reservationId,
                state.reservationState,
                state.paymentId,
                state.paymentState,
                state.shipmentId,
                state.shipmentState,
                log);
    }

    /**
     * Lost-id recovery applies to technical activity failures (timeouts / retryable HTTP), not to
     * non-retryable {@code BUSINESS} outcomes (workflow or activity).
     */
    private static boolean shouldAttemptLostIdRecovery(Exception cause) {
        Throwable t = cause;
        while (t != null) {
            if (t instanceof ApplicationFailure af
                    && ActivityPolicies.BUSINESS_FAILURE_TYPE.equals(af.getType())) {
                return false;
            }
            t = t.getCause();
        }
        return cause instanceof ActivityFailure;
    }

    /**
     * D-208 Option B: at most one idempotent re-POST per missing create id (same keys/payloads).
     * Failures here do not invent FAILED; they leave uncertainty for Option E.
     */
    private void recoverMissingCreateIds(SagaState state, List<String> log) {
        if (state.reserveAttempted && state.reservationId == null) {
            try {
                ReserveResponse reserved = recoveryActivities.reserve(
                        state.orderId, state.sku, state.qty);
                state.reservationId = reserved.reservationId();
                state.reservationState = reserved.state();
                log.add("recover-reserve");
            } catch (RuntimeException ex) {
                log.add("recover-reserve-failed:" + ex.getMessage());
            }
        }
        if (state.chargeAttempted && state.paymentId == null) {
            try {
                // Same IdempotencyKeys + charge payload as the forward attempt (D-015 / D-208).
                ChargeResponse charged = recoveryActivities.charge(
                        state.orderId, state.amount, state.chargeFaultHint);
                state.paymentId = charged.paymentId();
                state.paymentState = charged.state();
                log.add("recover-charge");
                if (state.paymentState == PaymentState.UNKNOWN
                        || state.paymentState == PaymentState.PENDING) {
                    if (state.pendingOutcome == null) {
                        state.pendingOutcome = BusinessOutcome.AMBIGUOUS;
                    }
                }
            } catch (RuntimeException ex) {
                log.add("recover-charge-failed:" + ex.getMessage());
            }
        }
        if (state.arrangeAttempted && state.shipmentId == null) {
            try {
                // Same IdempotencyKeys + arrange payload + fault hint as the forward attempt.
                ArrangeResponse arranged =
                        recoveryActivities.arrange(state.orderId, state.arrangeFaultHint);
                state.shipmentId = arranged.shipmentId();
                state.shipmentState = arranged.state();
                log.add("recover-arrange");
            } catch (RuntimeException ex) {
                log.add("recover-arrange-failed:" + ex.getMessage());
            }
        }
    }

    private void runCompensation(SagaState state, List<String> log) {
        // 1) Cancel shipment if ARRANGED (reconcile first)
        if (state.shipmentId != null) {
            ShipmentSnapshot shipment = compensationActivities.getShipment(state.shipmentId);
            state.shipmentState = shipment.state();
            if (shipment.state() == ShipmentState.ARRANGED) {
                CancelShipmentResponse cancelled = compensationActivities.cancelShipment(state.shipmentId);
                require(cancelled.state() == ShipmentState.CANCELLED,
                        "cancel expected CANCELLED, got " + cancelled.state());
                state.shipmentState = cancelled.state();
                log.add("cancel");
            } else {
                log.add("cancel-skipped:" + shipment.state());
            }
        } else {
            log.add("cancel-skipped:none");
        }

        // F3-Unresolved (D-AUTH-S3-5): experimental opacity left payment unconfirmed over HTTP.
        // Do not unhinted-GET CAPTURED and refund/release — cell expects CAPTURED + HELD.
        if (leaveDanglingCaptureForExperimentalOpacity(state)) {
            log.add("refund-skipped:experimental-opacity-unconfirmed");
            log.add("release-skipped:experimental-opacity-unconfirmed");
            return;
        }

        // 2) Refund only if CAPTURED confirmed (unhinted GET — opacity window not on this path)
        if (state.paymentId != null) {
            PaymentSnapshot payment = compensationActivities.getPayment(state.paymentId, null);
            state.paymentState = payment.state();
            if (payment.state() == PaymentState.CAPTURED) {
                RefundResponse refunded = compensationActivities.refund(state.paymentId);
                require(refunded.state() == PaymentState.REFUNDED,
                        "refund expected REFUNDED, got " + refunded.state());
                state.paymentState = refunded.state();
                log.add("refund");
            } else {
                log.add("refund-skipped:" + payment.state());
            }
        } else {
            log.add("refund-skipped:none");
        }

        // 3) Release only if HELD (never CONSUMED)
        if (state.reservationId != null) {
            ReservationSnapshot reservation = compensationActivities.getReservation(state.reservationId);
            state.reservationState = reservation.state();
            if (reservation.state() == ReservationState.HELD) {
                ReleaseResponse released = compensationActivities.release(state.reservationId);
                require(released.state() == ReservationState.RELEASED,
                        "release expected RELEASED, got " + released.state());
                state.reservationState = released.state();
                log.add("release");
            } else {
                log.add("release-skipped:" + reservation.state());
            }
        } else {
            log.add("release-skipped:none");
        }
    }

    /**
     * Known resources still requiring compensation after mutate attempts → incomplete (throw).
     */
    private void assertNoOutstandingCompensationRequired(SagaState state, List<String> log) {
        if (leaveDanglingCaptureForExperimentalOpacity(state)) {
            // Intentional CAPTURED + HELD for F3-Unresolved experimental cell.
            return;
        }
        if (state.shipmentId != null) {
            ShipmentSnapshot shipment = compensationActivities.getShipment(state.shipmentId);
            state.shipmentState = shipment.state();
            if (shipment.state() == ShipmentState.ARRANGED) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Shipment still ARRANGED after compensation; log=" + log,
                        ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE);
            }
        }
        if (state.paymentId != null) {
            PaymentSnapshot payment = compensationActivities.getPayment(state.paymentId, null);
            state.paymentState = payment.state();
            if (payment.state() == PaymentState.CAPTURED) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Payment still CAPTURED after compensation; log=" + log,
                        ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE);
            }
        }
        if (state.reservationId != null) {
            ReservationSnapshot reservation = compensationActivities.getReservation(state.reservationId);
            state.reservationState = reservation.state();
            if (reservation.state() == ReservationState.HELD) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Reservation still HELD after compensation; log=" + log,
                        ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE);
            }
        }
    }

    private void refreshSnapshots(SagaState state) {
        if (state.reservationId != null) {
            state.reservationState = compensationActivities.getReservation(state.reservationId).state();
        }
        if (state.paymentId != null) {
            // Unhinted: after experimental resolution, refresh may observe DB CAPTURED for honesty.
            state.paymentState = compensationActivities.getPayment(state.paymentId, null).state();
        }
        if (state.shipmentId != null) {
            state.shipmentState = compensationActivities.getShipment(state.shipmentId).state();
        }
    }

    /**
     * D-AUTH-S3-5 / F3-Unresolved: forward path never confirmed CAPTURED over HTTP while
     * {@code RESOLVE_TRANSPORT_OPAQUE} was armed; leave CAPTURED+HELD (no refund/release).
     */
    private static boolean leaveDanglingCaptureForExperimentalOpacity(SagaState state) {
        if (state.pendingOutcome != BusinessOutcome.AMBIGUOUS) {
            return false;
        }
        if (!isTransportOpacityHint(state.resolveFaultHint)) {
            return false;
        }
        return state.paymentState == PaymentState.UNKNOWN
                || state.paymentState == PaymentState.PENDING;
    }

    private static boolean isTransportOpacityHint(String hint) {
        return hint != null && "RESOLVE_TRANSPORT_OPAQUE".equals(hint.trim());
    }

    /**
     * True only when the armed resolve hint is transport opacity and the activity failure chain
     * contains a retryable {@code HTTP_503} {@link ApplicationFailure} (Temporal wraps it under
     * {@link ActivityFailure} after exhausting retries).
     */
    private static boolean isExhaustedTransportOpacityFailure(ActivityFailure failure, String resolveHint) {
        if (!isTransportOpacityHint(resolveHint)) {
            return false;
        }
        Throwable t = failure;
        while (t != null) {
            if (t instanceof ApplicationFailure af && "HTTP_503".equals(af.getType())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /** D-208 Option E — honest terminal labeling. */
    private static BusinessOutcome resolveHonestOutcome(SagaState state) {
        if (state.pendingOutcome == BusinessOutcome.AMBIGUOUS) {
            return BusinessOutcome.AMBIGUOUS;
        }
        // After lost-id recovery, still-missing create ids ⇒ uncertain side effect (not compensated).
        // BUSINESS rejects that never committed a row leave ids null without recovery — not AMBIGUOUS.
        if (state.lostIdRecoveryAttempted) {
            if (state.reserveAttempted && state.reservationId == null) {
                return BusinessOutcome.AMBIGUOUS;
            }
            if (state.chargeAttempted && state.paymentId == null) {
                return BusinessOutcome.AMBIGUOUS;
            }
            if (state.arrangeAttempted && state.shipmentId == null) {
                return BusinessOutcome.AMBIGUOUS;
            }
        }
        // Authoritative UNKNOWN/PENDING payment is not a confirmed compensated failure
        if (state.paymentId != null
                && (state.paymentState == PaymentState.UNKNOWN
                        || state.paymentState == PaymentState.PENDING)) {
            return BusinessOutcome.AMBIGUOUS;
        }
        if (state.pendingOutcome != null) {
            return state.pendingOutcome;
        }
        return BusinessOutcome.COMPENSATED_FAILURE;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw ApplicationFailure.newNonRetryableFailure(message, ActivityPolicies.BUSINESS_FAILURE_TYPE);
        }
    }

    private static final class SagaState {
        final UUID orderId;
        final String sku;
        final int qty;
        final Money amount;
        final String chargeFaultHint;
        final String resolveFaultHint;
        final String arrangeFaultHint;

        boolean reserveAttempted;
        boolean chargeAttempted;
        boolean arrangeAttempted;
        boolean lostIdRecoveryAttempted;

        UUID reservationId;
        ReservationState reservationState;
        UUID paymentId;
        PaymentState paymentState;
        UUID shipmentId;
        ShipmentState shipmentState;
        BusinessOutcome pendingOutcome;
        String forwardFailure;

        SagaState(OrderFulfillmentInput input) {
            this.orderId = input.orderId();
            this.sku = input.sku();
            this.qty = input.qty();
            this.amount = input.amount();
            this.chargeFaultHint = input.chargeFaultHint();
            this.resolveFaultHint = input.resolveFaultHint();
            this.arrangeFaultHint = input.arrangeFaultHint();
        }

        OrderFulfillmentResult successResult() {
            return new OrderFulfillmentResult(
                    orderId,
                    BusinessOutcome.SUCCESS,
                    reservationId,
                    reservationState,
                    paymentId,
                    paymentState,
                    shipmentId,
                    shipmentState,
                    List.of());
        }
    }
}
