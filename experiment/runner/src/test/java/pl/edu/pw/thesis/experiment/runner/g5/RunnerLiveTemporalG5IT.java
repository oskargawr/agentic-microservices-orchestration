package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import pl.edu.pw.thesis.experiment.oracle.IndependentOracle;
import pl.edu.pw.thesis.experiment.runner.config.JdbcAllowlist;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.fault.F0FaultController;
import pl.edu.pw.thesis.experiment.runner.lifecycle.TrialRunner;
import pl.edu.pw.thesis.experiment.runner.lock.ExperimentEnvLock;
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
import pl.edu.pw.thesis.experiment.runner.variant.VariantAAdapter;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalClients;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

/**
 * TH-402 G5 live Temporal runner pre-pilot (D-AUTH-G5).
 *
 * <p><b>Opt-in only:</b> requires {@code THESIS_RUN_G5=true}. Default Gradle/CI must leave this
 * unset so this class is disabled and starts neither Docker nor workers.
 *
 * <p><b>Live execution</b> remains separately Owner-authorized; this delivery must not run with
 * the flag set.
 *
 * <p>G5-2 and G5-3 are independent scenarios (separate methods / stacks). Protocol timings are
 * unmodified ({@link RunnerTiming#protocolDefaults()}).
 */
@EnabledIf("liveG5Enabled")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunnerLiveTemporalG5IT {

    @TempDir
    static Path resultsRoot;

    static boolean liveG5Enabled() {
        return G5OptIn.liveG5Requested();
    }

    @BeforeAll
    static void requireInfrastructureWhenOptedIn() {
        if (!G5LabSupport.postgresAvailable() || !G5LabSupport.temporalAvailable()) {
            throw new IllegalStateException(
                    "THESIS_RUN_G5=true but Postgres and/or Temporal are unreachable. "
                            + "Start infra (docker compose) and business DB before opt-in G5. "
                            + "jdbc="
                            + G5LabSupport.JDBC
                            + " temporal="
                            + G5LabSupport.TEMPORAL_TARGET);
        }
        if (!G5LabSupport.dedicatedEnvironmentConfirmed()) {
            throw new IllegalStateException(
                    "THESIS_RUN_G5=true requires THESIS_G5_DEDICATED_ENV=true "
                            + "(operator confirmation of an isolated Temporal lab environment).");
        }
    }

    @Test
    @Order(1)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void g5_2_naturalCompletion_withWorker_fullR14_oracleOnce_ownP1() throws Exception {
        assumeTrue(G5OptIn.scenarioEnabled("G5-2"), "THESIS_RUN_G5_SCENARIO excludes G5-2");

        try (G5LabSupport.ServiceStack stack = new G5LabSupport.ServiceStack()) {
            // G5-2 boot order: services → worker up → measured trial
            stack.startServices();
            stack.startWorkerPolling();

            String expId = "g5-2-" + Instant.now().toEpochMilli();
            Path expResults = resultsRoot.resolve(expId);
            Files.createDirectories(expResults);

            WorkflowServiceStubs stubs = TemporalClients.newServiceStubs(temporalSettings());
            try (ExperimentEnvLock lock = ExperimentEnvLock.acquire(resultsRoot, "g5-2-lab");
                    HikariDataSource ds = dataSource()) {
                RunnerClock clock = RunnerClock.system();
                WorkflowClient client = TemporalClients.newWorkflowClient(stubs, temporalSettings());
                CountingVariantAdapter adapter = new CountingVariantAdapter(
                        new VariantAAdapter(client, stubs, temporalSettings().namespace(), clock));

                ExperimentDefinition experiment = experiment(expId);
                RunnerEnvironment env = environment(expId, expResults);
                FileResultStore store = new FileResultStore(expResults, experiment);
                store.saveExperiment(experiment);

                TrialRunner runner = new TrialRunner(
                        experiment,
                        env,
                        new StateReset(env, ds),
                        new F0FaultController(clock),
                        adapter,
                        new IndependentOracle(ds),
                        store,
                        clock);

                UUID orderId = UUID.randomUUID();
                TrialSpec spec = TrialSpec.forVariantA(
                        expId, "trial-g5-2", "F0", 0, orderId, BusinessInputs.protocolPilotDefaults());

                TrialRecord record = runner.run(spec);

                // G5-1 (prefix): ids + describe
                assertThat(record.workflowId()).isEqualTo(spec.workflowId()).isNotBlank();
                assertThat(record.runId()).isNotBlank();
                assertDescribe(stubs, record.workflowId(), record.runId());
                assertThat(OrderFulfillmentWorkflow.TASK_QUEUE).isEqualTo("variant-a-order-fulfillment");

                // G5-2: natural completion — no measured terminate
                assertThat(adapter.terminateCalls())
                        .as("G5-2 must not invoke VariantAAdapter.terminate")
                        .isZero();
                assertThat(record.terminatedBy()).isEqualTo(TerminatedBy.WORKFLOW_STOP);
                assertThat(record.temporalStatus()).isEqualTo("COMPLETED");
                assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
                assertThat(record.workflowQuiesced()).isTrue();

                // G5-4: Oracle once (persisted summary); CONFIRMED_SUCCESS for F0 package
                assertThat(record.oracle()).isNotNull();
                assertThat(record.oracle().classification()).isEqualTo("CONFIRMED_SUCCESS");
                assertThat(record.tOracle()).isNotNull();
                assertThat(record.tTerminal()).isNotNull();

                // OWN-P1: durable JSON exists after COMPLETED
                assertThat(store.trialJsonPath(spec.trialId())).exists();
            } finally {
                stubs.shutdown();
            }
        }
    }

    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void g5_3_noPoller_tRunMax_terminate_historyGate_acceptanceArtifact() throws Exception {
        assumeTrue(G5OptIn.scenarioEnabled("G5-3"), "THESIS_RUN_G5_SCENARIO excludes G5-3");

        // G5-3: do NOT start a worker. Separate from G5-2 stack.
        G5IsolationPreflight.Result preflight = new G5IsolationPreflight(
                        new EnvFlagG5QueuePollerProbe(), G5LabSupport.dedicatedEnvironmentConfirmed())
                .evaluate();
        preflight.requireOk();

        // Services optional for empty-DB Oracle path; start them so StateReset/Oracle JDBC work.
        try (G5LabSupport.ServiceStack stack = new G5LabSupport.ServiceStack()) {
            stack.startServices();
            // Explicit: no startWorkerPolling()

            String expId = "g5-3-" + Instant.now().toEpochMilli();
            Path expResults = resultsRoot.resolve(expId);
            Files.createDirectories(expResults);

            WorkflowServiceStubs stubs = TemporalClients.newServiceStubs(temporalSettings());
            try (ExperimentEnvLock lock = ExperimentEnvLock.acquire(resultsRoot, "g5-3-lab");
                    HikariDataSource ds = dataSource()) {
                RunnerClock clock = RunnerClock.system();
                WorkflowClient client = TemporalClients.newWorkflowClient(stubs, temporalSettings());
                CountingVariantAdapter adapter = new CountingVariantAdapter(
                        new VariantAAdapter(client, stubs, temporalSettings().namespace(), clock));

                ExperimentDefinition experiment = experiment(expId);
                RunnerEnvironment env = environment(expId, expResults);
                FileResultStore store = new FileResultStore(expResults, experiment);
                store.saveExperiment(experiment);

                TrialRunner runner = new TrialRunner(
                        experiment,
                        env,
                        new StateReset(env, ds),
                        new F0FaultController(clock),
                        adapter,
                        new IndependentOracle(ds),
                        store,
                        clock);

                UUID orderId = UUID.randomUUID();
                TrialSpec spec = TrialSpec.forVariantA(
                        expId, "trial-g5-3", "F0", 0, orderId, BusinessInputs.protocolPilotDefaults());

                TrialRecord beforeAcceptance = runner.run(spec);

                // Measured R6 path
                assertThat(adapter.terminateCalls()).isEqualTo(1);
                assertThat(beforeAcceptance.terminatedBy()).isEqualTo(TerminatedBy.T_RUN_MAX);
                assertThat(beforeAcceptance.temporalStatus()).isEqualTo("TERMINATED");
                assertThat(beforeAcceptance.workflowId()).isEqualTo(spec.workflowId());
                assertThat(beforeAcceptance.runId()).isNotBlank();

                if (beforeAcceptance.harnessStatus() == HarnessStatus.COMPLETED) {
                    assertThat(beforeAcceptance.oracle()).isNotNull();
                    assertThat(beforeAcceptance.tOracle()).isNotNull();
                }

                // History gate + acceptance — never mutates TrialRecord
                G5HistoryFetcher fetcher =
                        new G5HistoryFetcher(stubs, temporalSettings().namespace());
                List<HistoryEvent> events =
                        fetcher.fetch(beforeAcceptance.workflowId(), beforeAcceptance.runId());
                Path historyPath = expResults.resolve("history").resolve("g5-3-history.tsv");
                G5HistoryFetcher.writeDiagnosticExport(historyPath, events);

                G5HistoryValidationResult validation = G5HistoryGate.validateTemporalHistory(events);
                G5AcceptanceRecord acceptance = G5AcceptanceRecord.fromValidation(
                        beforeAcceptance.workflowId(),
                        beforeAcceptance.runId(),
                        beforeAcceptance.gitSha(),
                        preflight.preflightResult(),
                        preflight.diagnostics(),
                        "GetWorkflowExecutionHistory",
                        historyPath.toString(),
                        validation,
                        Instant.now());
                Path acceptancePath = new G5AcceptanceWriter().write(expResults, acceptance);

                TrialRecord afterAcceptance = store.readTrial(spec.trialId());
                G5AcceptanceWriter.assertUnchanged(beforeAcceptance, afterAcceptance);
                assertThat(acceptancePath).exists();
                assertThat(acceptance.scenarioId()).isEqualTo("G5-3");
                assertThat(acceptance.acceptanceResult())
                        .isIn(G5AcceptanceRecord.VALID, G5AcceptanceRecord.INVALID);
            } finally {
                stubs.shutdown();
            }
        }
    }

    private static void assertDescribe(WorkflowServiceStubs stubs, String workflowId, String runId) {
        var execution = io.temporal.api.common.v1.WorkflowExecution.newBuilder()
                .setWorkflowId(workflowId)
                .setRunId(runId)
                .build();
        var describe = stubs.blockingStub()
                .describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder()
                        .setNamespace(temporalSettings().namespace())
                        .setExecution(execution)
                        .build());
        assertThat(describe.getWorkflowExecutionInfo().getExecution().getWorkflowId())
                .isEqualTo(workflowId);
        assertThat(describe.getWorkflowExecutionInfo().getExecution().getRunId()).isEqualTo(runId);
        assertThat(describe.getWorkflowExecutionInfo().getTaskQueue())
                .isEqualTo(OrderFulfillmentWorkflow.TASK_QUEUE);
    }

    private static TemporalSettings temporalSettings() {
        return new TemporalSettings(G5LabSupport.TEMPORAL_TARGET, G5LabSupport.TEMPORAL_NAMESPACE);
    }

    private static HikariDataSource dataSource() {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(G5LabSupport.JDBC);
        hc.setUsername(G5LabSupport.USER);
        hc.setPassword(G5LabSupport.PASS);
        hc.setMaximumPoolSize(4);
        return new HikariDataSource(hc);
    }

    private static ExperimentDefinition experiment(String expId) {
        String gitSha = System.getenv().getOrDefault("GIT_SHA", "g5-local");
        return new ExperimentDefinition(
                expId,
                "protocol-v0",
                "pre-pilot",
                List.of("A"),
                List.of("F0"),
                1,
                BusinessInputs.protocolPilotDefaults(),
                RunnerTiming.protocolDefaults(),
                gitSha,
                Instant.now());
    }

    private static RunnerEnvironment environment(String envId, Path results) {
        return new RunnerEnvironment(
                envId,
                G5LabSupport.JDBC,
                G5LabSupport.USER,
                G5LabSupport.PASS,
                new JdbcAllowlist(Set.of(G5LabSupport.JDBC)),
                results,
                temporalSettings(),
                RunnerTiming.protocolDefaults());
    }
}
