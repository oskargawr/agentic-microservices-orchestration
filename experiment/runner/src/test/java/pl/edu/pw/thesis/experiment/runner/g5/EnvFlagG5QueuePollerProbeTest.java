package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;

class EnvFlagG5QueuePollerProbeTest {

    @Test
    void unitTestDouble_explicitFalse() {
        assertThat(new EnvFlagG5QueuePollerProbe(false)
                        .anyPollerDetected(OrderFulfillmentWorkflow.TASK_QUEUE))
                .isFalse();
    }

    @Test
    void unitTestDouble_explicitTrue() {
        assertThat(new EnvFlagG5QueuePollerProbe(true)
                        .anyPollerDetected(OrderFulfillmentWorkflow.TASK_QUEUE))
                .isTrue();
    }
}
