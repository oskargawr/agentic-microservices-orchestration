package pl.edu.pw.thesis.experiment.runner.fault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

class F0FaultControllerTest {

    private static final RunnerClock CLOCK = new RunnerClock() {
        @Override
        public Instant now() {
            return Instant.parse("2026-11-02T00:00:00Z");
        }

        @Override
        public void sleep(Duration duration) {}
    };

    @Test
    void armVerifyDisarmIsNoOpInjection() {
        F0FaultController controller = new F0FaultController(CLOCK);
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t1", "F0", 0, orderId, BusinessInputs.protocolPilotDefaults());

        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        assertThat(armed.mechanism()).isEqualTo("NONE");
        assertThat(controller.verify(armed, trial).ok()).isTrue();
        controller.disarm("t1");
        assertThat(controller.describe("F0").mechanism()).isEqualTo("NONE");
    }

    @Test
    void rejectsNonF0() {
        F0FaultController controller = new F0FaultController(CLOCK);
        assertThatThrownBy(() -> controller.arm(new FaultArmRequest("t", "F1", UUID.randomUUID(), "A")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
