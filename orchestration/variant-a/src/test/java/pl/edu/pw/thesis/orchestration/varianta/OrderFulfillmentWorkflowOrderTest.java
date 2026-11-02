package pl.edu.pw.thesis.orchestration.varianta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.orchestration.varianta.model.BusinessOutcome;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

class OrderFulfillmentWorkflowOrderTest {

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;
    private RecordingActivities activities;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        client = testEnv.getWorkflowClient();
        Worker worker = testEnv.newWorker(OrderFulfillmentWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
        activities = new RecordingActivities();
        worker.registerActivitiesImplementations(activities);
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    private OrderFulfillmentWorkflow stub() {
        return client.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(OrderFulfillmentWorkflow.TASK_QUEUE)
                        .setWorkflowId("order-" + UUID.randomUUID())
                        .build());
    }

    @Test
    void happyPathCallsReserveChargeArrangeConsumeInOrder() {
        UUID orderId = UUID.randomUUID();
        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                orderId, "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(activities.operations()).containsExactly("reserve", "charge", "arrange", "consume");
        assertThat(result.outcome()).isEqualTo(BusinessOutcome.SUCCESS);
        assertThat(result.reservationState()).isEqualTo(ReservationState.CONSUMED);
        assertThat(result.paymentState()).isEqualTo(PaymentState.CAPTURED);
        assertThat(result.shipmentState()).isEqualTo(ShipmentState.ARRANGED);
        assertThat(result.matchesConfirmedSuccessSnapshot()).isTrue();
    }

    @Test
    void t201_1_failureAfterReserveReleasesHeld() {
        activities.chargeReturns(PaymentState.FAILED);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.paymentState()).isEqualTo(PaymentState.FAILED);
        assertThat(result.compensationLog()).contains("release");
        assertThat(result.compensationLog()).anyMatch(s -> s.startsWith("refund-skipped"));
        assertThat(activities.operations()).containsSubsequence("reserve", "charge", "release");
        assertThat(activities.operations()).doesNotContain("arrange", "refund");
    }

    @Test
    void t201_2_failureAfterCaptureRefundsThenReleases() {
        activities.failArrangeWithBusiness("arrange rejected");

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
        assertThat(result.paymentState()).isEqualTo(PaymentState.REFUNDED);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).containsExactly("cancel-skipped:none", "refund", "release");
        assertThat(activities.operations()).containsSubsequence("charge", "arrange", "refund", "release");
    }

    @Test
    void t201_3_failureAfterArrangeCancelsRefundsReleases() {
        activities.failConsumeWithBusiness("consume rejected");

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
        assertThat(result.shipmentState()).isEqualTo(ShipmentState.CANCELLED);
        assertThat(result.paymentState()).isEqualTo(PaymentState.REFUNDED);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).containsExactly("cancel", "refund", "release");
        assertThat(activities.operations())
                .containsSubsequence("arrange", "consume", "cancel", "refund", "release");
    }

    @Test
    void t201_4_unknownResolvesToCapturedContinuesHappyPath() {
        activities.chargeReturns(PaymentState.UNKNOWN);
        activities.resolveSequence(PaymentState.CAPTURED);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599), null, null, 1));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.SUCCESS);
        assertThat(result.matchesConfirmedSuccessSnapshot()).isTrue();
        assertThat(activities.operations()).containsSubsequence("charge", "resolve", "arrange", "consume");
        assertThat(activities.operations()).doesNotContain("refund", "release");
    }

    @Test
    void t201_5_unknownUnresolvedReturnsAmbiguousAndReleases() {
        activities.chargeReturns(PaymentState.UNKNOWN);
        activities.resolveSequence(PaymentState.UNKNOWN, PaymentState.UNKNOWN, PaymentState.UNKNOWN);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599), null, null, 3));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(result.paymentState()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).contains("release");
        assertThat(result.compensationLog()).anyMatch(s -> s.startsWith("refund-skipped"));
        assertThat(activities.operations()).doesNotContain("arrange", "refund");
    }

    @Test
    void t201_6_incompleteCompensationDoesNotReturnCompensatedFailure() {
        activities.failConsumeWithBusiness("consume rejected");
        activities.failRefundWithBusiness("refund unavailable");

        assertThatThrownBy(() -> stub().fulfill(new OrderFulfillmentInput(
                        UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599))))
                .isInstanceOf(WorkflowFailedException.class)
                .satisfies(ex -> {
                    Throwable cause = ex;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    assertThat(cause).isInstanceOf(ApplicationFailure.class);
                    assertThat(((ApplicationFailure) cause).getType())
                            .isEqualTo(ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE);
                });

        assertThat(activities.operations()).containsSubsequence("cancel", "refund");
        assertThat(activities.operations()).doesNotContain("release");
    }
}
