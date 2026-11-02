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

class CompositeFaultControllerTest {

    private static final RunnerClock CLOCK = new RunnerClock() {
        @Override
        public Instant now() {
            return Instant.parse("2026-11-02T00:00:00Z");
        }

        @Override
        public void sleep(Duration duration) {}
    };

    @Test
    void f0PathDoesNotCallPaymentVerifier() {
        AtomicInteger checks = new AtomicInteger();
        PaymentFaultInjectionVerifier verifier = () -> {
            checks.incrementAndGet();
            return PaymentInjectionCheckResult.enabled("should-not-run");
        };
        CompositeFaultController controller = CompositeFaultController.f0AndF1P(CLOCK, verifier);
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t0", "F0", 0, orderId, BusinessInputs.protocolPilotDefaults());

        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        assertThat(armed.mechanism()).isEqualTo("NONE");
        assertThat(controller.verify(armed, trial).ok()).isTrue();
        assertThat(checks.get()).isZero();
        controller.disarm("t0");
    }

    @Test
    void f1pPathRequiresVerifier() {
        AtomicInteger checks = new AtomicInteger();
        CompositeFaultController controller = CompositeFaultController.f0AndF1P(
                CLOCK,
                () -> {
                    checks.incrementAndGet();
                    return PaymentInjectionCheckResult.enabled("ok");
                });
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t1", "F1-P", 0, orderId, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        assertThat(controller.verify(armed, trial).ok()).isTrue();
        assertThat(checks.get()).isEqualTo(1);
        controller.disarm("t1");
    }

    @Test
    void rejectsUnknownProfile() {
        CompositeFaultController controller = CompositeFaultController.f0Only(CLOCK);
        assertThatThrownBy(() -> controller.arm(new FaultArmRequest("t", "F1-P", UUID.randomUUID(), "A")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("F1-P");
    }

    @Test
    void f2PathRequiresShippingVerifier() {
        AtomicInteger checks = new AtomicInteger();
        CompositeFaultController controller = CompositeFaultController.f0AndF2(
                CLOCK,
                () -> {
                    checks.incrementAndGet();
                    return PaymentInjectionCheckResult.enabled("ok");
                });
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t2", "F2", 0, orderId, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        assertThat(armed.faultProfileId()).isEqualTo("F2");
        assertThat(controller.verify(armed, trial).ok()).isTrue();
        assertThat(checks.get()).isEqualTo(1);
        controller.disarm("t2");
    }
}
