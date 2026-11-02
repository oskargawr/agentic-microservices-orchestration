package pl.edu.pw.thesis.experiment.runner.cli;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import pl.edu.pw.thesis.experiment.oracle.IndependentOracle;
import pl.edu.pw.thesis.experiment.runner.config.JdbcAllowlist;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.fault.F0FaultController;
import pl.edu.pw.thesis.experiment.runner.lifecycle.ExperimentAbort;
import pl.edu.pw.thesis.experiment.runner.lifecycle.TrialRunner;
import pl.edu.pw.thesis.experiment.runner.lock.ExperimentEnvLock;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.reset.StateReset;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;
import pl.edu.pw.thesis.experiment.runner.variant.VariantAAdapter;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalClients;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

/**
 * CLI entry for TH-402 phase-1 (library + CLI, D-R3). Not a Spring Boot HTTP service.
 *
 * <pre>
 * Required:
 *   --jdbc-url=jdbc:postgresql://localhost:5433/business
 *   --allowlist=jdbc:postgresql://localhost:5433/business
 * Optional:
 *   --jdbc-user=thesis --jdbc-password=thesis
 *   --results-dir=experiment/results
 *   --env-id=local-lab
 *   --experiment-id=exp-...
 *   --quiesce-wait-ms=5000
 *   --t-observe-ms=30000 --t-run-max-ms=120000
 * </pre>
 */
public final class RunnerMain {

    private RunnerMain() {}

    public static void main(String[] args) throws Exception {
        Args a = Args.parse(args);
        if (a.allowlistUrls.isEmpty()) {
            System.err.println("FATAL: --allowlist is required (fail-closed)");
            System.exit(2);
        }

        RunnerClock clock = RunnerClock.system();
        JdbcAllowlist allowlist = new JdbcAllowlist(a.allowlistUrls);
        TemporalSettings temporal = new TemporalSettings(a.temporalTarget, a.temporalNamespace);
        RunnerTiming timing = new RunnerTiming(
                Duration.ofMillis(a.tObserveMs),
                Duration.ofMillis(a.tRunMaxMs),
                Duration.ofMillis(a.quiesceWaitMs));

        RunnerEnvironment env = new RunnerEnvironment(
                a.envId,
                a.jdbcUrl,
                a.jdbcUser,
                a.jdbcPassword,
                allowlist,
                Path.of(a.resultsDir),
                temporal,
                timing);

        // Fail closed before lock / connections that would mutate
        env.assertSafeForDestructiveOps();

        ExperimentDefinition experiment = new ExperimentDefinition(
                a.experimentId,
                "protocol-v0",
                a.phase,
                List.of("A"),
                List.of(F0FaultController.PROFILE_F0),
                1,
                BusinessInputs.protocolPilotDefaults(),
                timing,
                a.gitSha,
                Instant.now());

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(a.jdbcUrl);
        hc.setUsername(a.jdbcUser);
        hc.setPassword(a.jdbcPassword);
        hc.setMaximumPoolSize(4);

        try (HikariDataSource ds = new HikariDataSource(hc);
                ExperimentEnvLock lock = ExperimentEnvLock.acquire(env.resultsRoot(), env.envId())) {
            FileResultStore store = new FileResultStore(env.resultsRoot(), experiment);
            store.saveExperiment(experiment);

            WorkflowServiceStubs stubs = TemporalClients.newServiceStubs(temporal);
            try {
                WorkflowClient client = TemporalClients.newWorkflowClient(stubs, temporal);
                VariantAAdapter adapter =
                        new VariantAAdapter(client, stubs, temporal.namespace(), clock);
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
                String trialId = "trial-" + orderId;
                TrialSpec spec = TrialSpec.forVariantA(
                        experiment.experimentId(),
                        trialId,
                        F0FaultController.PROFILE_F0,
                        0,
                        orderId,
                        experiment.businessDefaults());

                TrialRecord record = runner.run(spec);
                if (runner.wasOperatorInterrupted()) {
                    // OWN-P3: experiment-level abort; do not rewrite terminal trial JSON
                    ExperimentAbort.abortExperiment(
                            store, experiment.experimentId(), "OPERATOR_INTERRUPT", Instant.now());
                    System.err.println(
                            "experimentStatus=ABORTED_EXPERIMENT reason=OPERATOR_INTERRUPT");
                    System.exit(130);
                }
                System.out.println(
                        "trialId="
                                + record.trialId()
                                + " harnessStatus="
                                + record.harnessStatus()
                                + " oracle="
                                + (record.oracle() == null
                                        ? "null"
                                        : record.oracle().classification())
                                + " exclusion="
                                + record.exclusionReason());
                if (record.harnessStatus() != HarnessStatus.COMPLETED) {
                    System.exit(1);
                }
            } finally {
                stubs.shutdown();
            }
        }
    }

    static final class Args {
        String jdbcUrl;
        String jdbcUser = env("THESIS_JDBC_USER", "thesis");
        String jdbcPassword = env("THESIS_JDBC_PASSWORD", "thesis");
        Set<String> allowlistUrls = new LinkedHashSet<>();
        String resultsDir = "experiment/results";
        String envId = "local-lab";
        String experimentId = "exp-" + Instant.now().toEpochMilli();
        String phase = "pilot";
        String temporalTarget = env("TEMPORAL_TARGET", TemporalSettings.DEFAULT_TARGET);
        String temporalNamespace = env("TEMPORAL_NAMESPACE", TemporalSettings.DEFAULT_NAMESPACE);
        String gitSha = env("GIT_SHA", "unknown");
        long quiesceWaitMs = 5000;
        long tObserveMs = 30_000;
        long tRunMaxMs = 120_000;

        static Args parse(String[] args) {
            Args a = new Args();
            for (String arg : args) {
                if (arg.startsWith("--jdbc-url=")) {
                    a.jdbcUrl = arg.substring("--jdbc-url=".length());
                } else if (arg.startsWith("--jdbc-user=")) {
                    a.jdbcUser = arg.substring("--jdbc-user=".length());
                } else if (arg.startsWith("--jdbc-password=")) {
                    a.jdbcPassword = arg.substring("--jdbc-password=".length());
                } else if (arg.startsWith("--allowlist=")) {
                    a.allowlistUrls.add(arg.substring("--allowlist=".length()));
                } else if (arg.startsWith("--results-dir=")) {
                    a.resultsDir = arg.substring("--results-dir=".length());
                } else if (arg.startsWith("--env-id=")) {
                    a.envId = arg.substring("--env-id=".length());
                } else if (arg.startsWith("--experiment-id=")) {
                    a.experimentId = arg.substring("--experiment-id=".length());
                } else if (arg.startsWith("--phase=")) {
                    a.phase = arg.substring("--phase=".length());
                } else if (arg.startsWith("--temporal-target=")) {
                    a.temporalTarget = arg.substring("--temporal-target=".length());
                } else if (arg.startsWith("--quiesce-wait-ms=")) {
                    a.quiesceWaitMs = Long.parseLong(arg.substring("--quiesce-wait-ms=".length()));
                } else if (arg.startsWith("--t-observe-ms=")) {
                    a.tObserveMs = Long.parseLong(arg.substring("--t-observe-ms=".length()));
                } else if (arg.startsWith("--t-run-max-ms=")) {
                    a.tRunMaxMs = Long.parseLong(arg.substring("--t-run-max-ms=".length()));
                } else if (arg.startsWith("--git-sha=")) {
                    a.gitSha = arg.substring("--git-sha=".length());
                } else if ("--help".equals(arg)) {
                    System.out.println("See RunnerMain javadoc for flags.");
                    System.exit(0);
                } else {
                    throw new IllegalArgumentException("Unknown arg: " + arg);
                }
            }
            if (a.jdbcUrl == null || a.jdbcUrl.isBlank()) {
                a.jdbcUrl = env("THESIS_JDBC_URL", null);
            }
            if (a.jdbcUrl == null || a.jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("--jdbc-url is required");
            }
            if (a.allowlistUrls.isEmpty()) {
                String fromEnv = System.getenv("THESIS_JDBC_ALLOWLIST");
                if (fromEnv != null && !fromEnv.isBlank()) {
                    for (String part : fromEnv.split(",")) {
                        if (!part.isBlank()) {
                            a.allowlistUrls.add(part.trim());
                        }
                    }
                }
            }
            return a;
        }

        private static String env(String key, String defaultValue) {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defaultValue : v.trim();
        }
    }
}
