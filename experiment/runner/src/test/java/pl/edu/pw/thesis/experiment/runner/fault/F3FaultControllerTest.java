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

class F3FaultControllerTest {

    private static final RunnerClock CLOCK = new RunnerClock() {
        @Override
        public Instant now() {
            return Instant.parse("2026-11-03T00:00:00Z");
        }

        @Override
        public void sleep(Duration duration) {}
    };

    @Test
    void recovered_armVerifyDisarm() {
        AtomicInteger checks = new AtomicInteger();
        PaymentF3FaultInjectionVerifier verifier = new PaymentF3FaultInjectionVerifier() {
            @Override
            public PaymentInjectionCheckResult checkRecovered() {
                checks.incrementAndGet();
                return PaymentInjectionCheckResult.enabled("ok");
            }

            @Override
            public PaymentInjectionCheckResult checkUnresolved() {
                throw new AssertionError("not used");
            }
        };
        F3RecoveredFaultController controller = new F3RecoveredFaultController(CLOCK, verifier);
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t-r", F3RecoveredFaultController.PROFILE, 0, orderId,
                BusinessInputs.protocolPilotDefaults());

        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        assertThat(armed.effectiveConfig())
                .containsEntry("faultProfileId", "F3-Recovered")
                .containsEntry("chargeCode", "F3_CAPTURE_LOST_RESPONSE")
                .containsEntry("opacityMode", "header-presence")
                .doesNotContainKey("resolveCode");
        assertThat(controller.verify(armed, trial).ok()).isTrue();
        assertThat(checks.get()).isEqualTo(1);
        controller.disarm("t-r");
    }

    @Test
    void unresolved_armVerifyRequiresOpacity() {
        AtomicInteger checks = new AtomicInteger();
        PaymentF3FaultInjectionVerifier verifier = new PaymentF3FaultInjectionVerifier() {
            @Override
            public PaymentInjectionCheckResult checkRecovered() {
                throw new AssertionError("not used");
            }

            @Override
            public PaymentInjectionCheckResult checkUnresolved() {
                checks.incrementAndGet();
                return PaymentInjectionCheckResult.enabled("opaque-ok");
            }
        };
        F3UnresolvedFaultController controller = new F3UnresolvedFaultController(CLOCK, verifier);
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t-u", F3UnresolvedFaultController.PROFILE, 0, orderId,
                BusinessInputs.protocolPilotDefaults());

        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        assertThat(armed.effectiveConfig())
                .containsEntry("faultProfileId", "F3-Unresolved")
                .containsEntry("chargeCode", "F3_CAPTURE_LOST_RESPONSE")
                .containsEntry("resolveCode", "RESOLVE_TRANSPORT_OPAQUE")
                .containsEntry("opacityMode", "header-presence");
        assertThat(controller.verify(armed, trial).ok()).isTrue();
        assertThat(checks.get()).isEqualTo(1);
    }

    @Test
    void unresolved_verifyFailsClosed() {
        F3UnresolvedFaultController controller = new F3UnresolvedFaultController(
                CLOCK,
                new PaymentF3FaultInjectionVerifier() {
                    @Override
                    public PaymentInjectionCheckResult checkRecovered() {
                        return PaymentInjectionCheckResult.enabled("x");
                    }

                    @Override
                    public PaymentInjectionCheckResult checkUnresolved() {
                        return PaymentInjectionCheckResult.disabledOrUnknown("no 503");
                    }
                });
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t-bad", "F3-Unresolved", 0, orderId, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armed = controller.arm(FaultArmRequest.from(trial));
        FaultVerifyResult result = controller.verify(armed, trial);
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("PAYMENT_F3_UNRESOLVED_NOT_CONFIRMED");
    }

    @Test
    void rejectsWrongProfile() {
        F3RecoveredFaultController recovered = new F3RecoveredFaultController(
                CLOCK,
                new PaymentF3FaultInjectionVerifier() {
                    @Override
                    public PaymentInjectionCheckResult checkRecovered() {
                        return PaymentInjectionCheckResult.enabled("ok");
                    }

                    @Override
                    public PaymentInjectionCheckResult checkUnresolved() {
                        return PaymentInjectionCheckResult.enabled("ok");
                    }
                });
        assertThatThrownBy(() -> recovered.arm(new FaultArmRequest("t", "F3-Unresolved", UUID.randomUUID(), "A")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
