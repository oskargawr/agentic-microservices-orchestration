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

    @Test
    void variantAAllProfiles_registersCanonicalSet_andRoutesIndependently() {
        AtomicInteger f1 = new AtomicInteger();
        AtomicInteger f2 = new AtomicInteger();
        AtomicInteger f3r = new AtomicInteger();
        AtomicInteger f3u = new AtomicInteger();
        CompositeFaultController controller = CompositeFaultController.variantAAllProfiles(
                CLOCK,
                () -> {
                    f1.incrementAndGet();
                    return PaymentInjectionCheckResult.enabled("ok");
                },
                () -> {
                    f2.incrementAndGet();
                    return PaymentInjectionCheckResult.enabled("ok");
                },
                new PaymentF3FaultInjectionVerifier() {
                    @Override
                    public PaymentInjectionCheckResult checkRecovered() {
                        f3r.incrementAndGet();
                        return PaymentInjectionCheckResult.enabled("ok");
                    }

                    @Override
                    public PaymentInjectionCheckResult checkUnresolved() {
                        f3u.incrementAndGet();
                        return PaymentInjectionCheckResult.enabled("ok");
                    }
                });

        assertThat(controller.registeredProfiles())
                .containsExactly("F0", "F1-P", "F2", "F3-Recovered", "F3-Unresolved");

        for (String profile : CanonicalFaultProfiles.ALL) {
            UUID orderId = UUID.randomUUID();
            TrialSpec trial = TrialSpec.forVariantA(
                    "exp", "t-" + profile, profile, 0, orderId, BusinessInputs.protocolPilotDefaults());
            ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
            assertThat(armed.faultProfileId()).isEqualTo(profile);
            assertThat(controller.verify(armed, trial).ok()).isTrue();
            controller.disarm("t-" + profile);
        }
        assertThat(f1.get()).isEqualTo(1);
        assertThat(f2.get()).isEqualTo(1);
        assertThat(f3r.get()).isEqualTo(1);
        assertThat(f3u.get()).isEqualTo(1);
    }

    @Test
    void disarm_removesArmedState_idempotent_andInactiveControllersUnaffected() {
        F0FaultController f0 = new F0FaultController(CLOCK);
        F1PFaultController f1 = new F1PFaultController(
                CLOCK, () -> PaymentInjectionCheckResult.enabled("ok"));
        var map = new java.util.LinkedHashMap<String, FaultController>();
        map.put(F0FaultController.PROFILE_F0, f0);
        map.put(F1PFaultController.PROFILE_F1_P, f1);
        CompositeFaultController composite = new CompositeFaultController(map);

        UUID orderId = UUID.randomUUID();
        TrialSpec f1Trial = TrialSpec.forVariantA(
                "exp", "t-f1", "F1-P", 0, orderId, BusinessInputs.protocolPilotDefaults());
        composite.arm(FaultArmRequest.from(f1Trial));
        assertThat(f1.isArmed("t-f1")).isTrue();
        assertThat(f0.isArmed("t-f1")).isFalse();

        TrialSpec f0Other = TrialSpec.forVariantA(
                "exp", "t-f0-other", "F0", 0, UUID.randomUUID(), BusinessInputs.protocolPilotDefaults());
        f0.arm(FaultArmRequest.from(f0Other));
        assertThat(f0.isArmed("t-f0-other")).isTrue();

        composite.disarm("t-f1");
        assertThat(f1.isArmed("t-f1")).isFalse();
        assertThat(f0.isArmed("t-f0-other")).isTrue();

        composite.disarm("t-f1"); // second disarm is safe
        assertThat(f1.isArmed("t-f1")).isFalse();
        assertThat(f0.isArmed("t-f0-other")).isTrue();
    }
}
