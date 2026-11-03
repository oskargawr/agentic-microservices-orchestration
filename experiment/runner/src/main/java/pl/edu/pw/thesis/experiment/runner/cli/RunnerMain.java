package pl.edu.pw.thesis.experiment.runner.cli;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import pl.edu.pw.thesis.experiment.oracle.IndependentOracle;
import pl.edu.pw.thesis.experiment.runner.accept.CellOutcomeValidation;
import pl.edu.pw.thesis.experiment.runner.accept.MatrixSummary;
import pl.edu.pw.thesis.experiment.runner.config.JdbcAllowlist;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.fault.CanonicalFaultProfiles;
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
import pl.edu.pw.thesis.experiment.runner.lifecycle.MatrixExperimentDriver;
import pl.edu.pw.thesis.experiment.runner.lifecycle.TrialRunner;
import pl.edu.pw.thesis.experiment.runner.lock.ExperimentEnvLock;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.reset.StateReset;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;
import pl.edu.pw.thesis.experiment.runner.variant.VariantAAdapter;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalClients;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

/**
 * CLI entry for TH-402 / TH-400 (library + CLI, D-R3). Not a Spring Boot HTTP service.
 *
 * <pre>
 * Required:
 *   --jdbc-url=jdbc:postgresql://localhost:5433/business
 *   --allowlist=jdbc:postgresql://localhost:5433/business
 * Optional:
 *   --fault-profile=F0                 (repeatable; default single F0)
 *   --fault-profiles=F0,F1-P,F2,...    (comma list; S4 matrix)
 *   --n=1                              (repetitions per profile)
 *   --payment-base-url=...             (required for F1-P / F3-* / multi-profile matrix)
 *   --shipping-base-url=...            (required for F2 / multi-profile matrix)
 * Exit codes (S4-B):
 *   0 = all planned cells COMPLETED and matched expected Oracle classification
 *   1 = harness exclusion / empty / blocked / incomplete / not-executed
 *   3 = COMPLETED cell with classification mismatch
 *  130 = operator interrupt
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

        List<String> schedule = CanonicalFaultProfiles.normalizeSchedule(a.faultProfileIds);
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

        env.assertSafeForDestructiveOps();

        FaultController faultController = buildFaultController(schedule, a, clock);

        ExperimentDefinition experiment = new ExperimentDefinition(
                a.experimentId,
                "protocol-v0",
                a.phase,
                List.of("A"),
                schedule,
                a.n,
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

                MatrixExperimentDriver.MatrixRunResult result =
                        new MatrixExperimentDriver(experiment, runner, clock).runAll();
                store.saveMatrixSummary(result.summary());

                for (TrialRecord record : result.trials()) {
                    System.out.println(
                            "trialId="
                                    + record.trialId()
                                    + " profile="
                                    + record.faultProfileId()
                                    + " harnessStatus="
                                    + record.harnessStatus()
                                    + " oracle="
                                    + (record.oracle() == null
                                            ? "null"
                                            : record.oracle().classification())
                                    + " exclusion="
                                    + record.exclusionReason());
                }
                MatrixSummary summary = result.summary();
                System.out.println(
                        "matrix planned="
                                + summary.plannedCells()
                                + " executed="
                                + summary.executedCells()
                                + " matched="
                                + summary.matched()
                                + " mismatched="
                                + summary.mismatched()
                                + " excluded="
                                + summary.excluded()
                                + " notExecuted="
                                + summary.notExecuted()
                                + " allMatched="
                                + summary.allMatched());
                for (CellOutcomeValidation cell : summary.cells()) {
                    if (!cell.matched()) {
                        System.err.println(
                                "MISMATCH trialId="
                                        + cell.trialId()
                                        + " profile="
                                        + cell.faultProfileId()
                                        + " detail="
                                        + cell.detail());
                    }
                }

                if (runner.wasOperatorInterrupted() || result.aborted()) {
                    ExperimentAbort.abortExperiment(
                            store,
                            experiment.experimentId(),
                            result.abortReason() == null
                                    ? "OPERATOR_INTERRUPT"
                                    : result.abortReason(),
                            Instant.now());
                    System.err.println(
                            "experimentStatus=ABORTED_EXPERIMENT reason="
                                    + (result.abortReason() == null
                                            ? "OPERATOR_INTERRUPT"
                                            : result.abortReason()));
                    System.exit(130);
                }
                System.exit(summary.suggestedExitCode());
            } finally {
                stubs.shutdown();
            }
        }
    }

    /**
     * Schedule-aware controller construction: multi-profile matrix → full concurrent registration;
     * single profile → only the controllers required for that profile (plus F0 pairing).
     * Fail-closed verification for selected profiles is preserved.
     */
    static FaultController buildFaultController(List<String> schedule, Args a, RunnerClock clock) {
        List<String> normalized = CanonicalFaultProfiles.normalizeSchedule(schedule);
        if (normalized.size() == 1 && F0FaultController.PROFILE_F0.equals(normalized.getFirst())) {
            return CompositeFaultController.f0Only(clock);
        }
        if (CanonicalFaultProfiles.needsAllProfileRegistration(normalized)) {
            // Genuine multi-profile matrix: full F0–F3 registration (OD-FC). Both verifier URL
            // families may be required even if a given cell omits one profile.
            String payment = requirePayment(a, "multi-profile matrix");
            String shipping = requireShipping(a, "multi-profile matrix");
            return CompositeFaultController.variantAAllProfiles(
                    clock,
                    HttpPaymentFaultInjectionVerifier.forBaseUrl(payment),
                    HttpShippingFaultInjectionVerifier.forBaseUrl(shipping),
                    HttpPaymentF3FaultInjectionVerifier.forBaseUrl(payment));
        }
        String only = normalized.getFirst();
        return switch (only) {
            case F0FaultController.PROFILE_F0 -> CompositeFaultController.f0Only(clock);
            case F1PFaultController.PROFILE_F1_P -> CompositeFaultController.f0AndF1P(
                    clock, HttpPaymentFaultInjectionVerifier.forBaseUrl(requirePayment(a, only)));
            case F2FaultController.PROFILE_F2 -> CompositeFaultController.f0AndF2(
                    clock, HttpShippingFaultInjectionVerifier.forBaseUrl(requireShipping(a, only)));
            case F3RecoveredFaultController.PROFILE -> CompositeFaultController.f0AndF3Recovered(
                    clock, HttpPaymentF3FaultInjectionVerifier.forBaseUrl(requirePayment(a, only)));
            case F3UnresolvedFaultController.PROFILE -> CompositeFaultController.f0AndF3Unresolved(
                    clock, HttpPaymentF3FaultInjectionVerifier.forBaseUrl(requirePayment(a, only)));
            default -> throw new IllegalArgumentException("unsupported fault profile '" + only + "'");
        };
    }

    private static String requirePayment(Args a, String context) {
        if (a.paymentBaseUrl == null || a.paymentBaseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    context
                            + " requires --payment-base-url=... (or THESIS_PAYMENT_BASE_URL)");
        }
        return a.paymentBaseUrl.trim();
    }

    private static String requireShipping(Args a, String context) {
        if (a.shippingBaseUrl == null || a.shippingBaseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    context
                            + " requires --shipping-base-url=... (or THESIS_SHIPPING_BASE_URL)");
        }
        return a.shippingBaseUrl.trim();
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
        List<String> faultProfileIds = new ArrayList<>();
        int n = 1;
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
                } else if (arg.startsWith("--n=")) {
                    a.n = Integer.parseInt(arg.substring("--n=".length()));
                    if (a.n <= 0) {
                        throw new IllegalArgumentException("--n must be positive");
                    }
                } else if (arg.startsWith("--fault-profiles=")) {
                    String raw = arg.substring("--fault-profiles=".length()).trim();
                    for (String part : raw.split(",")) {
                        if (!part.isBlank()) {
                            a.faultProfileIds.add(part.trim());
                        }
                    }
                } else if (arg.startsWith("--fault-profile=")) {
                    a.faultProfileIds.add(arg.substring("--fault-profile=".length()).trim());
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
            if (a.faultProfileIds.isEmpty()) {
                a.faultProfileIds.add(F0FaultController.PROFILE_F0);
            }
            return a;
        }

        private static String env(String key, String defaultValue) {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defaultValue : v.trim();
        }
    }
}
