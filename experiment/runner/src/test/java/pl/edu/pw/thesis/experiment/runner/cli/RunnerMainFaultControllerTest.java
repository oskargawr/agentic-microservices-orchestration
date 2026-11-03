package pl.edu.pw.thesis.experiment.runner.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.experiment.runner.fault.CanonicalFaultProfiles;
import pl.edu.pw.thesis.experiment.runner.fault.CompositeFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.FaultController;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

class RunnerMainFaultControllerTest {

    private static final RunnerClock CLOCK = new RunnerClock() {
        @Override
        public Instant now() {
            return Instant.parse("2026-11-03T12:00:00Z");
        }

        @Override
        public void sleep(Duration duration) {}
    };

    private static RunnerMain.Args blankUrls() {
        RunnerMain.Args a = new RunnerMain.Args();
        a.paymentBaseUrl = null;
        a.shippingBaseUrl = null;
        return a;
    }

    @Test
    void f0Only_doesNotRequireServiceUrls() {
        FaultController c = RunnerMain.buildFaultController(List.of("F0"), blankUrls(), CLOCK);
        assertThat(c).isInstanceOf(CompositeFaultController.class);
        assertThat(((CompositeFaultController) c).registeredProfiles()).containsExactly("F0");
    }

    @Test
    void singleF2_requiresShipping_notPayment() {
        RunnerMain.Args a = blankUrls();
        a.shippingBaseUrl = "http://localhost:8083";
        FaultController c = RunnerMain.buildFaultController(List.of("F2"), a, CLOCK);
        assertThat(((CompositeFaultController) c).registeredProfiles()).containsExactly("F0", "F2");

        assertThatThrownBy(() -> RunnerMain.buildFaultController(List.of("F2"), blankUrls(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shipping-base-url");

        RunnerMain.Args paymentOnly = blankUrls();
        paymentOnly.paymentBaseUrl = "http://localhost:8082";
        assertThatThrownBy(() -> RunnerMain.buildFaultController(List.of("F2"), paymentOnly, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shipping-base-url");
    }

    @Test
    void singleF1P_requiresPayment_notShipping() {
        RunnerMain.Args a = blankUrls();
        a.paymentBaseUrl = "http://localhost:8082";
        FaultController c = RunnerMain.buildFaultController(List.of("F1-P"), a, CLOCK);
        assertThat(((CompositeFaultController) c).registeredProfiles()).containsExactly("F0", "F1-P");

        RunnerMain.Args shippingOnly = blankUrls();
        shippingOnly.shippingBaseUrl = "http://localhost:8083";
        assertThatThrownBy(() -> RunnerMain.buildFaultController(List.of("F1-P"), shippingOnly, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payment-base-url");
    }

    @Test
    void singleF3_requiresPayment_notShipping() {
        RunnerMain.Args a = blankUrls();
        a.paymentBaseUrl = "http://localhost:8082";
        assertThat(((CompositeFaultController)
                        RunnerMain.buildFaultController(List.of("F3-Recovered"), a, CLOCK))
                        .registeredProfiles())
                .containsExactly("F0", "F3-Recovered");
        assertThat(((CompositeFaultController)
                        RunnerMain.buildFaultController(List.of("F3-Unresolved"), a, CLOCK))
                        .registeredProfiles())
                .containsExactly("F0", "F3-Unresolved");
    }

    @Test
    void multiProfileMatrix_requiresBothUrls_andRegistersAllCanonical() {
        RunnerMain.Args a = blankUrls();
        a.paymentBaseUrl = "http://localhost:8082";
        a.shippingBaseUrl = "http://localhost:8083";
        CompositeFaultController c = (CompositeFaultController)
                RunnerMain.buildFaultController(List.of("F0", "F2"), a, CLOCK);
        assertThat(c.registeredProfiles()).containsExactlyElementsOf(CanonicalFaultProfiles.ALL);

        RunnerMain.Args noShip = blankUrls();
        noShip.paymentBaseUrl = "http://localhost:8082";
        assertThatThrownBy(() -> RunnerMain.buildFaultController(List.of("F0", "F1-P"), noShip, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shipping-base-url");
    }

    @Test
    void scheduleNormalization_isDeterministic() {
        assertThat(CanonicalFaultProfiles.normalizeSchedule(List.of("F2", "F0", "F2")))
                .containsExactly("F2", "F0");
        assertThat(CanonicalFaultProfiles.needsAllProfileRegistration(List.of("F2"))).isFalse();
        assertThat(CanonicalFaultProfiles.needsAllProfileRegistration(List.of("F0", "F2"))).isTrue();
    }
}
