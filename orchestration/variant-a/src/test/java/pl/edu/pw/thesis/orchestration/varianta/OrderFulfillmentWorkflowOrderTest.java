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
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
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

    @Test
    void happyPathCallsReserveChargeArrangeConsumeInOrder() {
        OrderFulfillmentWorkflow workflow = client.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(OrderFulfillmentWorkflow.TASK_QUEUE)
                        .setWorkflowId("order-" + UUID.randomUUID())
                        .build());

        UUID orderId = UUID.randomUUID();
        OrderFulfillmentResult result = workflow.fulfill(new OrderFulfillmentInput(
                orderId, "SKU-MVP-1", 1, Money.plnMinor(2599)));

        assertThat(activities.operations()).containsExactly("reserve", "charge", "arrange", "consume");
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.reservationState()).isEqualTo(ReservationState.CONSUMED);
        assertThat(result.paymentState()).isEqualTo(PaymentState.CAPTURED);
        assertThat(result.shipmentState()).isEqualTo(ShipmentState.ARRANGED);
        assertThat(result.matchesConfirmedSuccessSnapshot()).isTrue();
    }
}
