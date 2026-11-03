package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import pl.edu.pw.thesis.experiment.oracle.OracleEvaluationRequest;
import pl.edu.pw.thesis.experiment.oracle.OraclePattern;
import pl.edu.pw.thesis.experiment.oracle.OracleReasonCode;
import pl.edu.pw.thesis.experiment.oracle.OracleResult;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.runner.config.JdbcAllowlist;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.fault.F0FaultController;
import pl.edu.pw.thesis.experiment.runner.lifecycle.TrialRunner;
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

/**
 * G5-4 / D evidence: Oracle exactly-once is proven on the {@link TrialRunner} call path — not by a
 * standalone helper that calls Oracle once in isolation.
 */
@ExtendWith(MockitoExtension.class)
class G5OracleExactlyOnceViaTrialRunnerTest {

    @TempDir Path tmp;

    @Mock VariantAdapter variantAdapter;
    @Mock StateReset stateReset;

    FakeClock clock;
    ExperimentDefinition experiment;
    RunnerEnvironment environment;
    FileResultStore store;
    IndependentOracle oracle;
    AtomicInteger evaluateCalls;
    List<Duration> observeBeforeOracle;

    @BeforeEach
    void setUp() throws Exception {
        clock = new FakeClock(Instant.parse("2026-11-02T12:00:00Z"));
        RunnerTiming timing = RunnerTiming.protocolDefaults();
        experiment = new ExperimentDefinition(
                "exp-g5-oracle",
                "protocol-v0",
                "pre-pilot",
                List.of("A"),
                List.of("F0"),
                1,
                BusinessInputs.protocolPilotDefaults(),
                timing,
                "sha",
                Instant.parse("2026-11-02T12:00:00Z"));
        environment = new RunnerEnvironment(
                "g5-lab",
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

        evaluateCalls = new AtomicInteger();
        observeBeforeOracle = new ArrayList<>();
        oracle = mock(IndependentOracle.class);
        lenient()
                .when(oracle.evaluate(any(OracleEvaluationRequest.class)))
                .thenAnswer(inv -> {
                    evaluateCalls.incrementAndGet();
                    observeBeforeOracle.addAll(List.copyOf(clock.slept));
                    UUID orderId = inv.getArgument(0, OracleEvaluationRequest.class).orderId();
                    String trialId = inv.getArgument(0, OracleEvaluationRequest.class).trialId();
                    return oracleSuccess(orderId, trialId);
                });
    }

    @Test
    void trialRunner_invokesOracleExactlyOnce_onlyAfterFullTObserve() throws Exception {
        TrialRunner runner = new TrialRunner(
                experiment,
                environment,
                stateReset,
                new F0FaultController(clock),
                variantAdapter,
                oracle,
                store,
                clock);

        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "g5-oracle-1", "F0", 0, orderId, experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-1");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-02T12:00:10Z"), "SUCCESS"));

        assertThat(evaluateCalls.get()).isZero();
        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(evaluateCalls.get()).isEqualTo(1);
        verify(oracle, times(1)).evaluate(any(OracleEvaluationRequest.class));
        assertThat(observeBeforeOracle).contains(Duration.ofSeconds(30));

        // Acceptance / history layer must not re-invoke Oracle
        Path durable = store.trialJsonPath(spec.trialId());
        TrialRecord durableRead = store.readTrial(spec.trialId());
        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                record,
                durable,
                durableRead,
                evaluateCalls.get(),
                "PASS",
                "unit",
                "GetWorkflowExecutionHistory",
                "hist.tsv",
                G5HistoryGate.validate(List.of(
                        new G5HistoryEventView(1, G5HistoryEventKind.OTHER),
                        new G5HistoryEventView(2, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED))),
                Instant.parse("2026-11-02T12:01:00Z"));
        new G5AcceptanceWriter().write(tmp, acceptance);
        assertThat(evaluateCalls.get()).isEqualTo(1);
        // Natural COMPLETED path is not a G5-3 TERMINATED evidence package — acceptance INVALID is OK
        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
    }

    @Test
    void trialRunner_tRunMaxPath_stillOracleOnceAfterObserve() throws Exception {
        TrialRunner runner = new TrialRunner(
                experiment,
                environment,
                stateReset,
                new F0FaultController(clock),
                variantAdapter,
                oracle,
                store,
                clock);

        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "g5-oracle-2", "F0", 0, orderId, experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-2");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(
                        new WorkflowStopInfo("RUNNING", false, Instant.parse("2026-11-02T12:02:00Z"), null),
                        new WorkflowStopInfo(
                                "TERMINATED", true, Instant.parse("2026-11-02T12:02:01Z"), null));
        when(variantAdapter.describe(any()))
                .thenReturn(new WorkflowStopInfo("RUNNING", false, clock.now(), null));

        TrialRecord record = runner.run(spec);

        assertThat(record.terminatedBy()).isEqualTo(TerminatedBy.T_RUN_MAX);
        assertThat(evaluateCalls.get()).isEqualTo(1);
        assertThat(observeBeforeOracle).contains(Duration.ofSeconds(30));
    }

    @Test
    void trialRunner_notQuiesced_neverCallsOracle() throws Exception {
        TrialRunner runner = new TrialRunner(
                experiment,
                environment,
                stateReset,
                new F0FaultController(clock),
                variantAdapter,
                oracle,
                store,
                clock);

        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "g5-oracle-3", "F0", 0, orderId, experiment.businessDefaults());
        when(variantAdapter.start(any(), any()))
                .thenReturn(new ExecutionHandle(spec.workflowId(), "run-3"));
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo("RUNNING", false, clock.now(), null));
        when(variantAdapter.describe(any()))
                .thenReturn(new WorkflowStopInfo("RUNNING", false, clock.now(), null));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.EXCLUDED);
        assertThat(evaluateCalls.get()).isZero();
        verify(oracle, never()).evaluate(any(OracleEvaluationRequest.class));
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
        final ArrayList<Duration> slept = new ArrayList<>();

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
