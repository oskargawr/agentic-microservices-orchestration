package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DescribeTaskQueueG5QueuePollerProbeTest {

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
        // Operator ack without a successful empty probe must not pass when probe detects pollers.
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
