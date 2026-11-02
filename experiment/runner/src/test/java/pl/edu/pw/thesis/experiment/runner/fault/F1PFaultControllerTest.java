package pl.edu.pw.thesis.experiment.runner.fault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

class F1PFaultControllerTest {

    private static final RunnerClock CLOCK = new RunnerClock() {
        @Override
        public Instant now() {
            return Instant.parse("2026-11-02T00:00:00Z");
        }

        @Override
        public void sleep(Duration duration) {}
    };

    @Test
    void armVerifyDisarm_withConfirmedInjection() {
        AtomicInteger checks = new AtomicInteger();
        PaymentFaultInjectionVerifier verifier = () -> {
            checks.incrementAndGet();
            return PaymentInjectionCheckResult.enabled("ok");
        };
        F1PFaultController controller = new F1PFaultController(CLOCK, verifier);
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t-f1p", F1PFaultController.PROFILE_F1_P, 0, orderId,
                BusinessInputs.protocolPilotDefaults());

        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        assertThat(armed.faultProfileId()).isEqualTo("F1-P");
        assertThat(armed.mechanism()).isEqualTo("HEADER");
        assertThat(armed.effectiveConfig())
                .containsEntry("faultProfileId", "F1-P")
                .containsEntry("trialId", "t-f1p")
                .containsEntry("orderId", orderId.toString())
                .containsEntry("header", "X-Thesis-Fault")
                .containsEntry("code", "CHARGE_FAIL")
                .containsEntry("service", "payment");

        assertThat(controller.verify(armed, trial).ok()).isTrue();
        assertThat(checks.get()).isEqualTo(1);

        controller.disarm("t-f1p");
        assertThat(controller.describe("F1-P").faultProfileId()).isEqualTo("F1-P");
    }

    @Test
    void verifyFailsClosed_whenInjectionNotConfirmed() {
        F1PFaultController controller = new F1PFaultController(
                CLOCK, () -> PaymentInjectionCheckResult.disabledOrUnknown("CAPTURED"));
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t2", "F1-P", 0, orderId, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));

        FaultVerifyResult result = controller.verify(armed, trial);
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("PAYMENT_FAULT_INJECTION_NOT_CONFIRMED");
    }

    @Test
    void rejectsNonF1P() {
        F1PFaultController controller = new F1PFaultController(
                CLOCK, () -> PaymentInjectionCheckResult.enabled("ok"));
        assertThatThrownBy(() -> controller.arm(new FaultArmRequest("t", "F0", UUID.randomUUID(), "A")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.arm(new FaultArmRequest("t", "F1", UUID.randomUUID(), "A")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
