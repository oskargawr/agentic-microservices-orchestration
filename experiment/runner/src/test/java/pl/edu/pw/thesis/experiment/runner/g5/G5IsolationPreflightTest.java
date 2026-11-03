package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class G5IsolationPreflightTest {

    @Test
    void passesWhenDedicatedEnvAndNoPoller() {
        G5IsolationPreflight.Result r =
                new G5IsolationPreflight(queue -> false, true).evaluate();
        assertThat(r.ok()).isTrue();
        assertThat(r.preflightResult()).isEqualTo("PASS");
        r.requireOk();
    }

    @Test
    void failsWhenPollerDetected_beforeMeasuredTrial() {
        G5IsolationPreflight.Result r =
                new G5IsolationPreflight(queue -> true, true).evaluate();
        assertThat(r.ok()).isFalse();
        assertThat(r.failureReason()).contains("exclusivity");
        assertThatThrownBy(r::requireOk).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenDedicatedEnvNotConfirmed() {
        G5IsolationPreflight.Result r =
                new G5IsolationPreflight(queue -> false, false).evaluate();
        assertThat(r.ok()).isFalse();
        assertThat(r.failureReason()).contains("dedicated");
    }

    @Test
    void failsClosedWhenProbeThrows() {
        G5IsolationPreflight.Result r = new G5IsolationPreflight(
                        queue -> {
                            throw new IllegalStateException("probe boom");
                        },
                        true)
                .evaluate();
        assertThat(r.ok()).isFalse();
        assertThat(r.failureReason()).contains("probe boom");
    }
}
