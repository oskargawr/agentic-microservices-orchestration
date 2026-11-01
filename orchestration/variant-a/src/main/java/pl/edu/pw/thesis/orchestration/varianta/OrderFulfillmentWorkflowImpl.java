package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import pl.edu.pw.thesis.contracts.inventory.ConsumeResponse;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.inventory.ReserveResponse;
import pl.edu.pw.thesis.contracts.payment.ChargeResponse;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ArrangeResponse;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

/**
 * Deterministic Variant A happy path: reserve → charge → arrange → consume (C-101-1).
 */
public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    private final OrderFulfillmentActivities activities =
            Workflow.newActivityStub(OrderFulfillmentActivities.class, ActivityPolicies.defaultOptions());

    @Override
    public OrderFulfillmentResult fulfill(OrderFulfillmentInput input) {
        ReserveResponse reserved = activities.reserve(input.orderId(), input.sku(), input.qty());
        require(reserved.state() == ReservationState.HELD,
                "reserve expected HELD, got " + reserved.state());

        ChargeResponse charged = activities.charge(input.orderId(), input.amount());
        require(charged.state() == PaymentState.CAPTURED,
                "charge expected CAPTURED, got " + charged.state());

        ArrangeResponse arranged = activities.arrange(input.orderId());
        require(arranged.state() == ShipmentState.ARRANGED,
                "arrange expected ARRANGED, got " + arranged.state());

        // C-101-1: consume only after confirmed arrange
        ConsumeResponse consumed = activities.consume(reserved.reservationId());
        require(consumed.state() == ReservationState.CONSUMED,
                "consume expected CONSUMED, got " + consumed.state());

        return new OrderFulfillmentResult(
                input.orderId(),
                reserved.reservationId(),
                consumed.state(),
                charged.paymentId(),
                charged.state(),
                arranged.shipmentId(),
                arranged.state());
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw ApplicationFailure.newNonRetryableFailure(message, ActivityPolicies.BUSINESS_FAILURE_TYPE);
        }
    }
}
