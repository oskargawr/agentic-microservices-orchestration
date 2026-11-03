package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;

class EnvFlagG5QueuePollerProbeTest {

    @Test
    void default_noPoller() {
        // Ordinary test JVM must not set THESIS_G5_POLLER_DETECTED.
        assertThat(new EnvFlagG5QueuePollerProbe().anyPollerDetected(OrderFulfillmentWorkflow.TASK_QUEUE))
                .isFalse();
    }
}
