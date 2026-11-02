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
import pl.edu.pw.thesis.experiment.runner.fault.F0FaultController;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
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
class TrialRunnerLifecycleTest {

    @TempDir Path tmp;

    @Mock VariantAdapter variantAdapter;
    @Mock IndependentOracle oracle;
    @Mock StateReset stateReset;

    FakeClock clock;
    ExperimentDefinition experiment;
    RunnerEnvironment environment;
    FileResultStore store;
    TrialRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        clock = new FakeClock(Instant.parse("2026-11-02T12:00:00Z"));
        RunnerTiming timing = new RunnerTiming(
                Duration.ofSeconds(30), Duration.ofSeconds(120), Duration.ofSeconds(5));
        experiment = new ExperimentDefinition(
                "exp-test",
                "protocol-v0",
                "pilot",
                List.of("A"),
                List.of("F0"),
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
        runner = new TrialRunner(
                experiment,
                environment,
                stateReset,
                new F0FaultController(clock),
                variantAdapter,
                oracle,
                store,
                clock);
    }

    @Test
    void happyPathWorkflowStopThenFullObserveThenOracleOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t1", "F0", 0, orderId, experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-1");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-02T12:00:10Z"), "SUCCESS"));
        when(oracle.evaluate(any())).thenReturn(oracleSuccess(orderId, "t1"));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(record.includeInRqRates()).isTrue();
        assertThat(record.terminatedBy()).isEqualTo(TerminatedBy.WORKFLOW_STOP);
        assertThat(record.workflowQuiesced()).isTrue();
        assertThat(record.faultObservedAt()).isNull();
        assertThat(record.oracle().classification()).isEqualTo("CONFIRMED_SUCCESS");
        assertThat(clock.slept).contains(Duration.ofSeconds(30));
        verify(variantAdapter, never()).terminate(any(), any());
        verify(oracle).evaluate(any());
    }

    @Test
    void tRunMaxUsesTerminateNotCancelThenObserve() throws Exception {
        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t2", "F0", 0, orderId, experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-2");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(
                        new WorkflowStopInfo("RUNNING", false, Instant.parse("2026-11-02T12:02:00Z"), null),
                        new WorkflowStopInfo(
                                "TERMINATED", true, Instant.parse("2026-11-02T12:02:01Z"), null));
        when(oracle.evaluate(any())).thenReturn(oracleSuccess(orderId, "t2"));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(record.terminatedBy()).isEqualTo(TerminatedBy.T_RUN_MAX);
        verify(variantAdapter).terminate(handle, "T_run_max");
        assertThat(clock.slept).contains(Duration.ofSeconds(30));
    }

    @Test
    void notQuiescedExcludesAndBlocksNext() throws Exception {
        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t3", "F0", 0, orderId, experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-3");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo("RUNNING", false, clock.now(), null));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.EXCLUDED);
        assertThat(record.exclusionReason()).isEqualTo("WORKFLOW_NOT_QUIESCED");
        assertThat(runner.isFurtherTrialsBlocked()).isTrue();
        verify(oracle, never()).evaluate(any());
        verify(variantAdapter).terminate(any(), any());
    }

    @Test
    void wrongDatabaseExcludedBeforeReset() {
        RunnerEnvironment badEnv = new RunnerEnvironment(
                "lab",
                "jdbc:postgresql://localhost:5433/other",
                "thesis",
                "thesis",
                JdbcAllowlist.of("jdbc:postgresql://localhost:5433/business"),
                tmp,
                new TemporalSettings("localhost:7233", "default"),
                experiment.timing());
        TrialRunner badRunner = new TrialRunner(
                experiment,
                badEnv,
                stateReset,
                new F0FaultController(clock),
                variantAdapter,
                oracle,
                store,
                clock);
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(),
                "t4",
                "F0",
                0,
                UUID.randomUUID(),
                experiment.businessDefaults());

        TrialRecord record = badRunner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.EXCLUDED);
        assertThat(record.exclusionReason()).isEqualTo("WRONG_DATABASE");
    }

    private static OracleResult oracleSuccess(UUID orderId, String trialId) {
        BusinessSnapshot snapshot = new BusinessSnapshot(
                orderId,
                "SKU-MVP-1",
                List.of(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                List.of());
        return new OracleResult(
                BusinessOutcome.CONFIRMED_SUCCESS,
                OraclePattern.SUCCESS,
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
        Instant now;
        final java.util.ArrayList<Duration> slept = new java.util.ArrayList<>();

        FakeClock(Instant start) {
            this.now = start;
        }

        @Override
        public Instant now() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            slept.add(duration);
            now = now.plus(duration);
        }
    }
}
