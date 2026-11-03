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
import pl.edu.pw.thesis.experiment.runner.fault.CompositeFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F0FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F1PFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F2FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F3RecoveredFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F3UnresolvedFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.HttpPaymentF3FaultInjectionVerifier;
import pl.edu.pw.thesis.experiment.runner.fault.HttpPaymentFaultInjectionVerifier;
import pl.edu.pw.thesis.experiment.runner.fault.HttpShippingFaultInjectionVerifier;
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
 * CLI entry for TH-402 / TH-400 S1 (library + CLI, D-R3). Not a Spring Boot HTTP service.
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
 *   --fault-profile=F0          (default)
 *   --fault-profile=F1-P        (explicit; requires payment base URL)
 *   --fault-profile=F2          (explicit; requires shipping base URL)
 *   --payment-base-url=http://127.0.0.1:8082   (required for F1-P)
 *   --shipping-base-url=http://127.0.0.1:8083  (required for F2)
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

        FaultController faultController = buildFaultController(a, clock);

        ExperimentDefinition experiment = new ExperimentDefinition(
                a.experimentId,
                "protocol-v0",
                a.phase,
                List.of("A"),
                List.of(a.faultProfileId),
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
                        faultController,
                        adapter,
                        new IndependentOracle(ds),
                        store,
                        clock);

                UUID orderId = UUID.randomUUID();
                String trialId = "trial-" + orderId;
                TrialSpec spec = TrialSpec.forVariantA(
                        experiment.experimentId(),
                        trialId,
                        a.faultProfileId,
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

    static FaultController buildFaultController(Args a, RunnerClock clock) {
        if (F0FaultController.PROFILE_F0.equals(a.faultProfileId)) {
            return CompositeFaultController.f0Only(clock);
        }
        if (F1PFaultController.PROFILE_F1_P.equals(a.faultProfileId)) {
            if (a.paymentBaseUrl == null || a.paymentBaseUrl.isBlank()) {
                throw new IllegalArgumentException(
                        "F1-P requires --payment-base-url=... (or THESIS_PAYMENT_BASE_URL) "
                                + "for fail-closed injection preflight");
            }
            return CompositeFaultController.f0AndF1P(
                    clock, HttpPaymentFaultInjectionVerifier.forBaseUrl(a.paymentBaseUrl.trim()));
        }
        if (F2FaultController.PROFILE_F2.equals(a.faultProfileId)) {
            if (a.shippingBaseUrl == null || a.shippingBaseUrl.isBlank()) {
                throw new IllegalArgumentException(
                        "F2 requires --shipping-base-url=... (or THESIS_SHIPPING_BASE_URL) "
                                + "for fail-closed injection preflight");
            }
            return CompositeFaultController.f0AndF2(
                    clock, HttpShippingFaultInjectionVerifier.forBaseUrl(a.shippingBaseUrl.trim()));
        }
        if (F3RecoveredFaultController.PROFILE.equals(a.faultProfileId)) {
            if (a.paymentBaseUrl == null || a.paymentBaseUrl.isBlank()) {
                throw new IllegalArgumentException(
                        "F3-Recovered requires --payment-base-url=... (or THESIS_PAYMENT_BASE_URL) "
                                + "for fail-closed injection preflight");
            }
            return CompositeFaultController.f0AndF3Recovered(
                    clock, HttpPaymentF3FaultInjectionVerifier.forBaseUrl(a.paymentBaseUrl.trim()));
        }
        if (F3UnresolvedFaultController.PROFILE.equals(a.faultProfileId)) {
            if (a.paymentBaseUrl == null || a.paymentBaseUrl.isBlank()) {
                throw new IllegalArgumentException(
                        "F3-Unresolved requires --payment-base-url=... (or THESIS_PAYMENT_BASE_URL) "
                                + "for fail-closed injection preflight");
            }
            return CompositeFaultController.f0AndF3Unresolved(
                    clock, HttpPaymentF3FaultInjectionVerifier.forBaseUrl(a.paymentBaseUrl.trim()));
        }
        throw new IllegalArgumentException(
                "unsupported --fault-profile=" + a.faultProfileId
                        + " (supported: F0, F1-P, F2, F3-Recovered, F3-Unresolved; "
                        + "F1/F3 are not canonical ids)");
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
        String faultProfileId = F0FaultController.PROFILE_F0;
        String paymentBaseUrl = env("THESIS_PAYMENT_BASE_URL", null);
        String shippingBaseUrl = env("THESIS_SHIPPING_BASE_URL", null);
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
                } else if (arg.startsWith("--fault-profile=")) {
                    a.faultProfileId = arg.substring("--fault-profile=".length()).trim();
                } else if (arg.startsWith("--payment-base-url=")) {
                    a.paymentBaseUrl = arg.substring("--payment-base-url=".length()).trim();
                } else if (arg.startsWith("--shipping-base-url=")) {
                    a.shippingBaseUrl = arg.substring("--shipping-base-url=".length()).trim();
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
            if ("F1".equals(a.faultProfileId)) {
                throw new IllegalArgumentException(
                        "canonical profile id is F1-P (not F1); use --fault-profile=F1-P");
            }
            return a;
        }

        private static String env(String key, String defaultValue) {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defaultValue : v.trim();
        }
    }
}
