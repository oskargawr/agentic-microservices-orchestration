package pl.edu.pw.thesis.orchestration.varianta;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.orchestration.varianta.model.BusinessOutcome;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

/**
 * TH-202 — Temporal retry / timeout behavior via {@link TestWorkflowEnvironment}
 * (D-202-1…D-202-4 preserved).
 */
class OrderFulfillmentRetryTimeoutTest {

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
                        .setWorkflowId("th202-" + UUID.randomUUID())
                        .build());
    }

    @Test
    void t202_2_retryableFailureThenSuccessCompletesWorkflow() {
        activities.failChargeRetryableTimes(2);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.SUCCESS);
        assertThat(result.matchesConfirmedSuccessSnapshot()).isTrue();
        assertThat(activities.chargeInvocations()).isEqualTo(3);
        assertThat(activities.operations()).containsSubsequence("reserve", "charge", "arrange", "consume");
    }

    @Test
    void t202_3_businessFailureIsNotRetried_attemptCountIsOne() {
        activities.failArrangeWithBusiness("arrange rejected");

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
        assertThat(activities.arrangeInvocations()).isEqualTo(1);
        assertThat(result.paymentState()).isEqualTo(PaymentState.REFUNDED);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).containsExactly("cancel-skipped:none", "refund", "release");
        assertThat(activities.operations()).doesNotContain("consume");
    }

    @Test
    void t202_4_retryExhaustionWithKnownIdsCompensatesWithoutMarkingPaymentFailed() {
        activities.failArrangeAlwaysRetryable("arrange unavailable");

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        // D-208: technical arrange exhaustion triggers lost-id recovery; if shipment id still
        // missing → AMBIGUOUS (not COMPENSATED_FAILURE). Payment/reservation still compensated.
        assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(activities.arrangeInvocations()).isEqualTo(6); // 5 Temporal + 1 recover
        assertThat(result.paymentState()).isEqualTo(PaymentState.REFUNDED);
        assertThat(result.paymentState()).isNotEqualTo(PaymentState.FAILED);
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
        assertThat(result.compensationLog()).anyMatch(s -> s.startsWith("recover-arrange-failed"));
        assertThat(result.compensationLog()).contains("refund", "release");
        assertThat(activities.operations()).containsSubsequence("charge", "arrange", "getPayment", "refund", "release");
        assertThat(activities.operations()).doesNotContain("consume", "cancel");
    }

    @Test
    void t202_5_unknownResolveLoopIsBoundedSeparatelyFromTemporalRetries() {
        int resolveLimit = 3;
        activities.chargeReturns(PaymentState.UNKNOWN);
        activities.resolveSequence(
                PaymentState.UNKNOWN, PaymentState.UNKNOWN, PaymentState.UNKNOWN, PaymentState.UNKNOWN);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599), null, null, resolveLimit));

        assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(result.paymentState()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(activities.resolveInvocations()).isEqualTo(resolveLimit);
        assertThat(activities.operations()).doesNotContain("arrange", "refund");
        assertThat(result.compensationLog()).anyMatch(s -> s.startsWith("refund-skipped"));
        assertThat(result.reservationState()).isEqualTo(ReservationState.RELEASED);
    }

    /**
     * S4-C: quantitative measurement under {@link TestWorkflowEnvironment} (not a live Temporal
     * cluster). Temporal {@code maximumAttempts=5} means up to <strong>five total</strong> forward
     * activity executions (not five retries after an initial attempt). Exhausted retryable charge
     * then triggers a separate lost-id recovery stub with {@code maximumAttempts=1} → exactly one
     * recovery execution. BUSINESS non-retry path remains covered by
     * {@link #t202_3_businessFailureIsNotRetried_attemptCountIsOne}.
     */
    @Test
    void s4c_defaultOptions_exhaustedRetryableCharge_forwardFive_recoveryOne() {
        int forwardMax = ActivityPolicies.defaultOptions().getRetryOptions().getMaximumAttempts();
        assertThat(forwardMax).isEqualTo(5);
        assertThat(ActivityPolicies.lostIdRecoveryOptions().getRetryOptions().getMaximumAttempts())
                .isEqualTo(1);
        activities.failChargeRetryableTimes(forwardMax + 5);

        OrderFulfillmentResult result = stub().fulfill(new OrderFulfillmentInput(
                UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(activities.forwardChargeInvocations()).isEqualTo(5);
        assertThat(activities.recoveryChargeInvocations()).isEqualTo(1);
        assertThat(activities.chargeInvocations()).isEqualTo(6);
        assertThat(result.outcome()).isNotEqualTo(BusinessOutcome.SUCCESS);
        assertThat(activities.operations()).doesNotContain("consume");
    }
}
