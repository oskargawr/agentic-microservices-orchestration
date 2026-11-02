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

/** TH-208 / D-208 — lost-response-without-returned-ID recovery (B+E). */
class OrderFulfillmentLostIdRecoveryTest {

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
                        .setWorkflowId("th208-" + UUID.randomUUID())
                        .build());
    }

    @Test
    void t208_1_chargeLostBodyRecoveredByTemporalRetryContinuesHappyPath() {
        activities.chargeCommitThenLoseBody(2, PaymentState.CAPTURED);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.SUCCESS);
        assertThat(result.paymentId()).isNotNull();
        assertThat(result.paymentState()).isEqualTo(PaymentState.CAPTURED);
        assertThat(result.paymentState()).isNotEqualTo(PaymentState.FAILED);
        assertThat(activities.chargeInvocations()).isGreaterThanOrEqualTo(3);
        assertThat(result.compensationLog()).isEmpty();
    }

    @Test
    void t208_2_and_3_exhaustedRetriesThenRecoveryRefundsCaptured() {
        // 5 lost bodies → Temporal exhausts; recovery prelude (6th) returns CAPTURED id
        activities.chargeCommitThenLoseBody(5, PaymentState.CAPTURED);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
        assertThat(result.paymentId()).isNotNull();
        assertThat(result.paymentState()).isEqualTo(PaymentState.REFUNDED);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).contains("recover-charge", "refund", "release");
        assertThat(result.paymentState()).isNotEqualTo(PaymentState.FAILED);
        assertThat(activities.chargeInvocations()).isEqualTo(6);
    }

    @Test
    void t208_4_recoveryUnknownObserved_noRefund_ambiguous() {
        activities.chargeCommitThenLoseBody(5, PaymentState.UNKNOWN);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(result.paymentId()).isNotNull();
        assertThat(result.paymentState()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).contains("recover-charge", "release");
        assertThat(result.compensationLog()).anyMatch(s -> s.startsWith("refund-skipped"));
        assertThat(activities.operations()).doesNotContain("refund");
    }

    @Test
    void t208_5_recoveryCannotObtainIds_ambiguousNotCompensatedFailure() {
        activities.chargeCommitThenLoseBodyForever();

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(result.paymentId()).isNull();
        assertThat(result.compensationLog()).anyMatch(s -> s.startsWith("recover-charge-failed"));
        assertThat(result.compensationLog()).contains("refund-skipped:none");
        // Reservation was obtained before charge — must still be released
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
    }

    @Test
    void t208_6_reserveLostId_recoveryThenRelease() {
        activities.reserveCommitThenLoseBody(5);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        // Forward failed at reserve; recovery finds HELD; compensate releases; no payment attempted
        assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
        assertThat(result.reservationId()).isNotNull();
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).contains("recover-reserve", "release");
        assertThat(result.paymentId()).isNull();
        assertThat(activities.chargeInvocations()).isZero();
    }

    @Test
    void t208_7_arrangeLostId_recoveryThenCancelRefundRelease() {
        activities.arrangeCommitThenLoseBody(5);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
        assertThat(result.shipmentId()).isNotNull();
        assertThat(result.shipmentState()).isEqualTo(ShipmentState.CANCELLED);
        assertThat(result.paymentState()).isEqualTo(PaymentState.REFUNDED);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).contains("recover-arrange", "cancel", "refund", "release");
    }

    @Test
    void t400_s2_arrangeLostId_recoveryPassesArrangeFaultHint() {
        activities.arrangeCommitThenLoseBody(5);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(),
                "SKU-MVP-1",
                1,
                Money.plnMinor(2599),
                null,
                null,
                "ARRANGE_REJECTED",
                null));

        assertThat(result.compensationLog()).contains("recover-arrange");
        assertThat(activities.arrangeFaultHints())
                .isNotEmpty()
                .allMatch("ARRANGE_REJECTED"::equals);
        assertThat(activities.arrangeFaultHints().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void t208_8_f3StyleUnknownWithIdUnchanged_ambiguousPath() {
        activities.chargeReturns(PaymentState.UNKNOWN);
        activities.resolveSequence(PaymentState.UNKNOWN, PaymentState.UNKNOWN, PaymentState.UNKNOWN);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599), null, null, 3));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(result.paymentId()).isNotNull();
        assertThat(result.paymentState()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(result.compensationLog()).doesNotContain("recover-charge");
        assertThat(activities.operations()).doesNotContain("arrange");
    }

    @Test
    void t208_partialProgress_knownReserveMissingCharge_recovery() {
        activities.chargeCommitThenLoseBody(5, PaymentState.CAPTURED);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.reservationId()).isNotNull();
        assertThat(result.paymentId()).isNotNull();
        assertThat(result.compensationLog()).contains("recover-charge", "refund", "release");
        assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
    }

    @Test
    void t208_knownResourceIncompleteCompensation_throwsCompensationIncomplete() {
        activities.failArrangeWithBusiness("arrange rejected");
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
    }
}
