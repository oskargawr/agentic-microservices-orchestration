package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Workflow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 */
public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    private final OrderFulfillmentActivities activities =
            Workflow.newActivityStub(OrderFulfillmentActivities.class, ActivityPolicies.defaultOptions());
    private final OrderFulfillmentActivities compensationActivities =
            Workflow.newActivityStub(OrderFulfillmentActivities.class, ActivityPolicies.compensationOptions());

    @Override
    public OrderFulfillmentResult fulfill(OrderFulfillmentInput input) {
        SagaState state = new SagaState(input.orderId());

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
        ReserveResponse reserved = activities.reserve(input.orderId(), input.sku(), input.qty());
        require(reserved.state() == ReservationState.HELD,
                "reserve expected HELD, got " + reserved.state());
        state.reservationId = reserved.reservationId();
        state.reservationState = reserved.state();

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

        ArrangeResponse arranged = activities.arrange(input.orderId());
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
            snap = activities.resolvePayment(state.paymentId, input.resolveFaultHint());
            state.paymentState = snap.state();
            if (state.paymentState == PaymentState.CAPTURED
                    || state.paymentState == PaymentState.FAILED) {
                break;
            }
        }
        if (snap == null) {
            snap = activities.getPayment(state.paymentId);
            state.paymentState = snap.state();
        }

        if (state.paymentState == PaymentState.CAPTURED) {
            ArrangeResponse arranged = activities.arrange(input.orderId());
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
                runCompensation(state, log);
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

        BusinessOutcome outcome = state.pendingOutcome != null
                ? state.pendingOutcome
                : BusinessOutcome.COMPENSATED_FAILURE;

        if (outcome == BusinessOutcome.AMBIGUOUS) {
            return new OrderFulfillmentResult(
                    state.orderId,
                    BusinessOutcome.AMBIGUOUS,
                    state.reservationId,
                    state.reservationState,
                    state.paymentId,
                    state.paymentState,
                    state.shipmentId,
                    state.shipmentState,
                    log);
        }

        return new OrderFulfillmentResult(
                state.orderId,
                BusinessOutcome.COMPENSATED_FAILURE,
                state.reservationId,
                state.reservationState,
                state.paymentId,
                state.paymentState,
                state.shipmentId,
                state.shipmentState,
                log);
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

        // 2) Refund only if CAPTURED confirmed
        if (state.paymentId != null) {
            PaymentSnapshot payment = compensationActivities.getPayment(state.paymentId);
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

    private void refreshSnapshots(SagaState state) {
        if (state.reservationId != null) {
            state.reservationState = compensationActivities.getReservation(state.reservationId).state();
        }
        if (state.paymentId != null) {
            state.paymentState = compensationActivities.getPayment(state.paymentId).state();
        }
        if (state.shipmentId != null) {
            state.shipmentState = compensationActivities.getShipment(state.shipmentId).state();
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw ApplicationFailure.newNonRetryableFailure(message, ActivityPolicies.BUSINESS_FAILURE_TYPE);
        }
    }

    private static final class SagaState {
        final UUID orderId;
        UUID reservationId;
        ReservationState reservationState;
        UUID paymentId;
        PaymentState paymentState;
        UUID shipmentId;
        ShipmentState shipmentState;
        BusinessOutcome pendingOutcome;
        String forwardFailure;

        SagaState(UUID orderId) {
            this.orderId = orderId;
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
