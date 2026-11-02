package pl.edu.pw.thesis.experiment.runner.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
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
import pl.edu.pw.thesis.experiment.runner.fault.CompositeFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.PaymentInjectionCheckResult;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.reset.StateReset;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;
import pl.edu.pw.thesis.experiment.runner.variant.ExecutionHandle;
import pl.edu.pw.thesis.experiment.runner.variant.VariantAdapter;
import pl.edu.pw.thesis.experiment.runner.variant.WorkflowStopInfo;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

@ExtendWith(MockitoExtension.class)
class TrialRunnerF1PLifecycleTest {

    @TempDir Path tmp;

    @Mock VariantAdapter variantAdapter;
    @Mock IndependentOracle oracle;
    @Mock StateReset stateReset;

    FakeClock clock;
    ExperimentDefinition experiment;
    RunnerEnvironment environment;
    FileResultStore store;
    AtomicInteger injectionChecks;

    @BeforeEach
    void setUp() throws Exception {
        clock = new FakeClock(Instant.parse("2026-11-02T12:00:00Z"));
        injectionChecks = new AtomicInteger();
        RunnerTiming timing = new RunnerTiming(
                Duration.ofSeconds(30), Duration.ofSeconds(120), Duration.ofSeconds(5));
        experiment = new ExperimentDefinition(
                "exp-f1p",
                "protocol-v0",
                "pilot",
                List.of("A"),
                List.of("F1-P"),
                1,
                BusinessInputs.protocolPilotDefaults(),
                timing,
                "sha",
                Instant.parse("2026-11-02T12:00:00Z"));
        environment = new RunnerEnvironment(
                "lab",
                "jdbc:postgresql://localhost:5433/business",
                "thesis",
                "thesis",
                JdbcAllowlist.of("jdbc:postgresql://localhost:5433/business"),
                tmp,
                new TemporalSettings("localhost:7233", "default"),
                timing);
        store = new FileResultStore(tmp, experiment);
        store.saveExperiment(experiment);
        lenient()
                .when(stateReset.resetToProtocolBaseline(anyString()))
                .thenReturn(new StateReset.BaselineSnapshot("SKU-MVP-1", 100, 0));
    }

    @Test
    void f1pHappyPath_persistsArmedConfig_andPassesArmedToAdapter() throws Exception {
        TrialRunner runner = newRunner(true);
        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t-f1p", "F1-P", 0, orderId, experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-1");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-02T12:00:10Z"), "FAILED"));
        when(oracle.evaluate(any())).thenReturn(oracleConfirmedFailure(orderId, "t-f1p"));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(record.faultProfileId()).isEqualTo("F1-P");
        assertThat(record.orderId()).isEqualTo(orderId);
        assertThat(record.trialId()).isEqualTo("t-f1p");
        assertThat(record.faultArmedRecord()).isInstanceOf(ArmedFaultRecord.class);
        @SuppressWarnings("unchecked")
        ArmedFaultRecord persisted = (ArmedFaultRecord) record.faultArmedRecord();
        assertThat(persisted.effectiveConfig())
                .containsEntry("faultProfileId", "F1-P")
                .containsEntry("trialId", "t-f1p")
                .containsEntry("orderId", orderId.toString())
                .containsEntry("code", "CHARGE_FAIL");
        assertThat(record.oracle().classification()).isEqualTo("CONFIRMED_FAILURE");
        // Pattern may be F2 for FAILED+RELEASED; acceptance gates on classification only
        assertThat(injectionChecks.get()).isEqualTo(1);

        ArgumentCaptor<ArmedFaultRecord> armedCaptor = ArgumentCaptor.forClass(ArmedFaultRecord.class);
        verify(variantAdapter).start(any(), armedCaptor.capture());
        assertThat(armedCaptor.getValue().effectiveConfig()).containsEntry("code", "CHARGE_FAIL");
    }

    @Test
    void f1pVerifyFails_whenInjectionNotConfirmed_doesNotStartWorkflow() throws Exception {
        TrialRunner runner = newRunner(false);
        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t-bad", "F1-P", 0, orderId, experiment.businessDefaults());

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.EXCLUDED);
        assertThat(record.exclusionReason()).isEqualTo("FAULT_VERIFY_FAILED");
        assertThat(record.infraError()).contains("PAYMENT_FAULT_INJECTION_NOT_CONFIRMED");
        verify(variantAdapter, never()).start(any(), any());
    }

    private TrialRunner newRunner(boolean injectionEnabled) {
        return new TrialRunner(
                experiment,
                environment,
                stateReset,
                CompositeFaultController.f0AndF1P(
                        clock,
                        () -> {
                            injectionChecks.incrementAndGet();
                            return injectionEnabled
                                    ? PaymentInjectionCheckResult.enabled("ok")
                                    : PaymentInjectionCheckResult.disabledOrUnknown("CAPTURED");
                        }),
                variantAdapter,
                oracle,
                store,
                clock);
    }

    private static OracleResult oracleConfirmedFailure(UUID orderId, String trialId) {
        BusinessSnapshot snapshot = new BusinessSnapshot(
                orderId,
                "SKU-MVP-1",
                List.of(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                List.of());
        return new OracleResult(
                BusinessOutcome.CONFIRMED_FAILURE,
                OraclePattern.F2,
                OracleReasonCode.NONE,
                List.of(),
                List.of(),
                snapshot,
                "order-" + orderId,
                trialId,
                OracleResult.EVALUATOR_VERSION,
                Instant.parse("2026-11-02T12:00:40Z"));
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
