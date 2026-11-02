package pl.edu.pw.thesis.experiment.runner.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.fault.F0FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F1PFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.FaultArmRequest;
import pl.edu.pw.thesis.experiment.runner.fault.PaymentInjectionCheckResult;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

class VariantAAdapterFaultHintTest {

    private static final RunnerClock CLOCK = new RunnerClock() {
        @Override
        public Instant now() {
            return Instant.parse("2026-11-02T00:00:00Z");
        }

        @Override
        public void sleep(java.time.Duration duration) {}
    };

    @Test
    void f0Armed_yieldsNullHint() {
        F0FaultController f0 = new F0FaultController(CLOCK);
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t0", "F0", 0, orderId, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armed = f0.arm(FaultArmRequest.from(trial));
        assertThat(VariantAAdapter.resolveChargeFaultHint(armed)).isNull();
        assertThat(VariantAAdapter.resolveChargeFaultHint(null)).isNull();
    }

    @Test
    void f1pArmed_yieldsChargeFail() {
        F1PFaultController f1p = new F1PFaultController(
                CLOCK, () -> PaymentInjectionCheckResult.enabled("ok"));
        UUID orderId = UUID.randomUUID();
        TrialSpec trial = TrialSpec.forVariantA(
                "exp", "t1", "F1-P", 0, orderId, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armed = f1p.arm(FaultArmRequest.from(trial));
        assertThat(VariantAAdapter.resolveChargeFaultHint(armed)).isEqualTo("CHARGE_FAIL");
    }

    @Test
    void noCrossTrialLeakage_afterDisarmF0FollowOn() {
        F1PFaultController f1p = new F1PFaultController(
                CLOCK, () -> PaymentInjectionCheckResult.enabled("ok"));
        F0FaultController f0 = new F0FaultController(CLOCK);

        UUID order1 = UUID.randomUUID();
        TrialSpec t1 = TrialSpec.forVariantA(
                "exp", "trial-1", "F1-P", 0, order1, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armedF1 = f1p.arm(FaultArmRequest.from(t1));
        assertThat(VariantAAdapter.resolveChargeFaultHint(armedF1)).isEqualTo("CHARGE_FAIL");
        f1p.disarm("trial-1");

        UUID order2 = UUID.randomUUID();
        TrialSpec t2 = TrialSpec.forVariantA(
                "exp", "trial-2", "F0", 0, order2, BusinessInputs.protocolPilotDefaults());
        ArmedFaultRecord armedF0 = f0.arm(FaultArmRequest.from(t2));
        assertThat(VariantAAdapter.resolveChargeFaultHint(armedF0)).isNull();
    }

    @Test
    void rejectsUnsupportedProfile() {
        ArmedFaultRecord weird = new ArmedFaultRecord(
                "F2", "HEADER", Map.of("code", "X"), Instant.parse("2026-11-02T00:00:00Z"));
        assertThatThrownBy(() -> VariantAAdapter.resolveChargeFaultHint(weird))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("F2");
    }
}
