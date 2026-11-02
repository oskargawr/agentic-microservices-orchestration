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

class F2FaultControllerTest {

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
        ShippingFaultInjectionVerifier verifier = () -> {
            checks.incrementAndGet();
            return PaymentInjectionCheckResult.enabled("ok");
        };
        F2FaultController controller = new F2FaultController(CLOCK, verifier);
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t-f2", F2FaultController.PROFILE_F2, 0, orderId,
                BusinessInputs.protocolPilotDefaults());

        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        assertThat(armed.faultProfileId()).isEqualTo("F2");
        assertThat(armed.mechanism()).isEqualTo("HEADER");
        assertThat(armed.effectiveConfig())
                .containsEntry("faultProfileId", "F2")
                .containsEntry("trialId", "t-f2")
                .containsEntry("orderId", orderId.toString())
                .containsEntry("header", "X-Thesis-Fault")
                .containsEntry("code", "ARRANGE_REJECTED")
                .containsEntry("service", "shipping");

        assertThat(controller.verify(armed, trial).ok()).isTrue();
        assertThat(checks.get()).isEqualTo(1);

        controller.disarm("t-f2");
        assertThat(controller.describe("F2").faultProfileId()).isEqualTo("F2");
    }

    @Test
    void verifyFailsClosed_whenInjectionNotConfirmed() {
        F2FaultController controller = new F2FaultController(
                CLOCK, () -> PaymentInjectionCheckResult.disabledOrUnknown("HTTP 200"));
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t2", "F2", 0, orderId, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));

        FaultVerifyResult result = controller.verify(armed, trial);
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("SHIPPING_FAULT_INJECTION_NOT_CONFIRMED");
    }

    @Test
    void rejectsNonF2() {
        F2FaultController controller = new F2FaultController(
                CLOCK, () -> PaymentInjectionCheckResult.enabled("ok"));
        assertThatThrownBy(() -> controller.arm(new FaultArmRequest("t", "F0", UUID.randomUUID(), "A")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.arm(new FaultArmRequest("t", "F1-P", UUID.randomUUID(), "A")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
