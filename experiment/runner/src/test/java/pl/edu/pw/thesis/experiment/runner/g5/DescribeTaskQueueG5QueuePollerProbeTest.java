package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.api.enums.v1.TaskQueueType;
import io.temporal.api.taskqueue.v1.PollerInfo;
import io.temporal.api.workflowservice.v1.DescribeTaskQueueResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;

class DescribeTaskQueueG5QueuePollerProbeTest {

    @Test
    void emptyPollersOnWorkflowAndActivity_noPollerDetected() {
        AtomicInteger calls = new AtomicInteger();
        G5DescribeTaskQueueClient client = req -> {
            calls.incrementAndGet();
            assertThat(req.getReportPollers()).isTrue();
            assertThat(req.getTaskQueue().getName()).isEqualTo(OrderFulfillmentWorkflow.TASK_QUEUE);
            return DescribeTaskQueueResponse.newBuilder().build();
        };
        DescribeTaskQueueG5QueuePollerProbe probe = new DescribeTaskQueueG5QueuePollerProbe(client, "default");
        assertThat(probe.anyPollerDetected(OrderFulfillmentWorkflow.TASK_QUEUE)).isFalse();
        assertThat(calls.get()).isEqualTo(2); // workflow + activity
        assertThat(probe.lastSnapshot().probeSucceeded()).isTrue();
        assertThat(probe.lastSnapshot().pollerDetected()).isFalse();

        G5IsolationPreflight.Result preflight = new G5IsolationPreflight(probe, true).evaluate();
        assertThat(preflight.ok()).isTrue();
    }

    @Test
    void reportedPoller_failsExclusivity() {
        G5DescribeTaskQueueClient client = req -> {
            if (req.getTaskQueueType() == TaskQueueType.TASK_QUEUE_TYPE_WORKFLOW) {
                return DescribeTaskQueueResponse.newBuilder()
                        .addPollers(PollerInfo.newBuilder().setIdentity("worker-1").build())
                        .build();
            }
            return DescribeTaskQueueResponse.newBuilder().build();
        };
        DescribeTaskQueueG5QueuePollerProbe probe = new DescribeTaskQueueG5QueuePollerProbe(client, "default");
        assertThat(probe.anyPollerDetected(OrderFulfillmentWorkflow.TASK_QUEUE)).isTrue();
        assertThat(probe.lastSnapshot().pollerIdentities()).anyMatch(s -> s.contains("worker-1"));

        G5IsolationPreflight.Result preflight = new G5IsolationPreflight(probe, true).evaluate();
        assertThat(preflight.ok()).isFalse();
        assertThat(preflight.failureReason()).contains("exclusivity");
    }

    @Test
    void apiError_failClosedAsPollerDetected() {
        G5DescribeTaskQueueClient client = req -> {
            throw new IllegalStateException("temporal down");
        };
        DescribeTaskQueueG5QueuePollerProbe probe = new DescribeTaskQueueG5QueuePollerProbe(client, "default");
        assertThat(probe.anyPollerDetected(OrderFulfillmentWorkflow.TASK_QUEUE)).isTrue();
        assertThat(probe.lastSnapshot().probeSucceeded()).isFalse();
        assertThat(probe.lastSnapshot().failureReason()).contains("temporal down");

        G5IsolationPreflight.Result preflight = new G5IsolationPreflight(probe, true).evaluate();
        assertThat(preflight.ok()).isFalse();
    }

    @Test
    void isolationPreflight_failsClosedWhenDescribeProbeReportsFailure() {
        G5QueuePollerProbe failing = taskQueue -> {
            throw new IllegalStateException("temporal unreachable");
        };
        G5IsolationPreflight.Result r = new G5IsolationPreflight(failing, true).evaluate();
        assertThat(r.ok()).isFalse();
        assertThat(r.failureReason()).contains("temporal unreachable");
    }

    @Test
    void dedicatedEnvAlone_isNotPassWithoutSuccessfulEmptyPollerProbe() {
        G5IsolationPreflight.Result r =
                new G5IsolationPreflight(new EnvFlagG5QueuePollerProbe(true), true).evaluate();
        assertThat(r.ok()).isFalse();
        assertThat(r.diagnostics()).contains("dedicatedEnvNote=operator-ack-only-not-poller-proof");
    }

    @Test
    void envFlagProbe_isTestDoubleOnly_documented() {
        assertThat(EnvFlagG5QueuePollerProbe.ENV).isEqualTo("THESIS_G5_POLLER_DETECTED");
        assertThat(new EnvFlagG5QueuePollerProbe(false).anyPollerDetected("variant-a-order-fulfillment"))
                .isFalse();
    }
}
