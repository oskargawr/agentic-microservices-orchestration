package pl.edu.pw.thesis.experiment.runner.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.experiment.oracle.IndependentOracle;
import pl.edu.pw.thesis.experiment.oracle.OraclePattern;
import pl.edu.pw.thesis.experiment.oracle.OracleReasonCode;
import pl.edu.pw.thesis.experiment.oracle.OracleResult;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.runner.config.JdbcAllowlist;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.fault.CanonicalFaultProfiles;
import pl.edu.pw.thesis.experiment.runner.fault.CompositeFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.FaultArmRequest;
import pl.edu.pw.thesis.experiment.runner.fault.FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.FaultProfileDescriptor;
import pl.edu.pw.thesis.experiment.runner.fault.FaultVerifyResult;
import pl.edu.pw.thesis.experiment.runner.fault.PaymentF3FaultInjectionVerifier;
import pl.edu.pw.thesis.experiment.runner.fault.PaymentFaultInjectionVerifier;
import pl.edu.pw.thesis.experiment.runner.fault.PaymentInjectionCheckResult;
import pl.edu.pw.thesis.experiment.runner.fault.ShippingFaultInjectionVerifier;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.reset.StateReset;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;
import pl.edu.pw.thesis.experiment.runner.variant.ExecutionHandle;
import pl.edu.pw.thesis.experiment.runner.variant.VariantAdapter;
import pl.edu.pw.thesis.experiment.runner.variant.WorkflowStopInfo;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

@ExtendWith(MockitoExtension.class)
class MatrixExperimentDriverTest {

    @TempDir Path tmp;

    @Mock VariantAdapter variantAdapter;
    @Mock IndependentOracle oracle;
    @Mock StateReset stateReset;

    FakeClock clock;
    AtomicInteger resets;

    @BeforeEach
    void setUp() throws Exception {
        clock = new FakeClock(Instant.parse("2026-11-03T12:00:00Z"));
        resets = new AtomicInteger();
        lenient()
                .when(stateReset.resetToProtocolBaseline(anyString()))
                .thenAnswer(inv -> {
                    resets.incrementAndGet();
                    return new StateReset.BaselineSnapshot("SKU-MVP-1", 100, 0);
                });
    }

    @Test
    void runsProfilesSerially_inScheduleOrder_withResetBetween() throws Exception {
        ExperimentDefinition experiment = experiment(List.of("F0", "F1-P"), 1);
        FileResultStore store = new FileResultStore(tmp, experiment);
        store.saveExperiment(experiment);
        TrialRunner runner = newRunner(experiment);

        when(variantAdapter.start(any(), any()))
                .thenAnswer(inv -> new ExecutionHandle(
                        inv.getArgument(0, pl.edu.pw.thesis.experiment.runner.model.TrialSpec.class)
                                .workflowId(),
                        "run"));
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-03T12:00:10Z"), "ok"));
        when(oracle.evaluate(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, pl.edu.pw.thesis.experiment.oracle.OracleEvaluationRequest.class);
            // First cell F0 → SUCCESS; second F1-P → FAILURE
            if (req.trialId().contains("F0")) {
                return oracleResult(req.orderId(), req.trialId(), BusinessOutcome.CONFIRMED_SUCCESS);
            }
            return oracleResult(req.orderId(), req.trialId(), BusinessOutcome.CONFIRMED_FAILURE);
        });

        MatrixExperimentDriver.MatrixRunResult result =
                new MatrixExperimentDriver(experiment, runner, clock).runAll();

        assertThat(result.trials()).hasSize(2);
        assertThat(result.trials().stream().map(TrialRecord::faultProfileId).toList())
                .containsExactly("F0", "F1-P");
        assertThat(result.trials()).allMatch(t -> t.harnessStatus() == HarnessStatus.COMPLETED);
        assertThat(resets.get()).isEqualTo(2);
        assertThat(result.summary().allMatched()).isTrue();
        assertThat(result.summary().suggestedExitCode()).isEqualTo(0);
        assertThat(CanonicalFaultProfiles.ALL)
                .containsExactly("F0", "F1-P", "F2", "F3-Recovered", "F3-Unresolved");
    }

    @Test
    void stopsWhenFurtherTrialsBlocked_afterDisarmFailure() throws Exception {
        ExperimentDefinition experiment = experiment(List.of("F0", "F1-P"), 1);
        FileResultStore store = new FileResultStore(tmp, experiment);
        store.saveExperiment(experiment);

        AtomicInteger disarmCount = new AtomicInteger();
        var map = new java.util.LinkedHashMap<String, pl.edu.pw.thesis.experiment.runner.fault.FaultController>();
        CompositeFaultController all = CompositeFaultController.variantAAllProfiles(
                clock,
                () -> PaymentInjectionCheckResult.enabled("ok"),
                () -> PaymentInjectionCheckResult.enabled("ok"),
                alwaysOkF3());
        FaultController failingDisarm = new FaultController() {
            @Override
            public ArmedFaultRecord arm(FaultArmRequest request) {
                return all.arm(request);
            }

            @Override
            public FaultVerifyResult verify(ArmedFaultRecord armed, TrialSpec trial) {
                return all.verify(armed, trial);
            }

            @Override
            public void disarm(String trialId) {
                disarmCount.incrementAndGet();
                if (disarmCount.get() == 1) {
                    throw new IllegalStateException("disarm boom");
                }
                all.disarm(trialId);
            }

            @Override
            public FaultProfileDescriptor describe(String faultProfileId) {
                return all.describe(faultProfileId);
            }
        };

        TrialRunner runner = new TrialRunner(
                experiment,
                env(experiment),
                stateReset,
                failingDisarm,
                variantAdapter,
                oracle,
                store,
                clock);

        when(variantAdapter.start(any(), any()))
                .thenAnswer(inv -> new ExecutionHandle(
                        inv.getArgument(0, pl.edu.pw.thesis.experiment.runner.model.TrialSpec.class)
                                .workflowId(),
                        "run"));
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-03T12:00:10Z"), "ok"));
        when(oracle.evaluate(any()))
                .thenAnswer(inv -> {
                    var req = inv.getArgument(0, pl.edu.pw.thesis.experiment.oracle.OracleEvaluationRequest.class);
                    return oracleResult(req.orderId(), req.trialId(), BusinessOutcome.CONFIRMED_SUCCESS);
                });

        MatrixExperimentDriver.MatrixRunResult result =
                new MatrixExperimentDriver(experiment, runner, clock).runAll();

        assertThat(result.trials()).hasSize(1);
        assertThat(result.aborted()).isTrue();
        assertThat(runner.isFurtherTrialsBlocked()).isTrue();
    }

    private TrialRunner newRunner(ExperimentDefinition experiment) {
        return new TrialRunner(
                experiment,
                env(experiment),
                stateReset,
                CompositeFaultController.variantAAllProfiles(
                        clock,
                        (PaymentFaultInjectionVerifier) () -> PaymentInjectionCheckResult.enabled("ok"),
                        (ShippingFaultInjectionVerifier) () -> PaymentInjectionCheckResult.enabled("ok"),
                        alwaysOkF3()),
                variantAdapter,
                oracle,
                new FileResultStore(tmp, experiment),
                clock);
    }

    private RunnerEnvironment env(ExperimentDefinition experiment) {
        return new RunnerEnvironment(
                "lab",
                "jdbc:postgresql://localhost:5433/business",
                "thesis",
                "thesis",
                JdbcAllowlist.of("jdbc:postgresql://localhost:5433/business"),
                tmp,
                new TemporalSettings("localhost:7233", "default"),
                experiment.timing());
    }

    private PaymentF3FaultInjectionVerifier alwaysOkF3() {
        return new PaymentF3FaultInjectionVerifier() {
            @Override
            public PaymentInjectionCheckResult checkRecovered() {
                return PaymentInjectionCheckResult.enabled("ok");
            }

            @Override
            public PaymentInjectionCheckResult checkUnresolved() {
                return PaymentInjectionCheckResult.enabled("ok");
            }
        };
    }

    private ExperimentDefinition experiment(List<String> profiles, int n) {
        return new ExperimentDefinition(
                "exp-matrix",
                "protocol-v0",
                "pilot",
                List.of("A"),
                profiles,
                n,
                BusinessInputs.protocolPilotDefaults(),
                new RunnerTiming(Duration.ofSeconds(30), Duration.ofSeconds(120), Duration.ofSeconds(5)),
                "sha",
                Instant.parse("2026-11-03T12:00:00Z"));
    }

    private static OracleResult oracleResult(UUID orderId, String trialId, BusinessOutcome outcome) {
        return new OracleResult(
                outcome,
                OraclePattern.NONE,
                OracleReasonCode.NONE,
                List.of(),
                List.of(),
                new BusinessSnapshot(orderId, "SKU-MVP-1", List.of(), Optional.empty(), Optional.empty(), Optional.empty(), List.of()),
                "order-" + orderId,
                trialId,
                OracleResult.EVALUATOR_VERSION,
                Instant.parse("2026-11-03T12:00:40Z"));
    }

    static final class FakeClock implements RunnerClock {
        private Instant now;

        FakeClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant now() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            now = now.plus(duration);
        }
    }
}
