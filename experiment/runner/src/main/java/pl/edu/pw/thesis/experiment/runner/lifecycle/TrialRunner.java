package pl.edu.pw.thesis.experiment.runner.lifecycle;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.oracle.IndependentOracle;
import pl.edu.pw.thesis.experiment.oracle.OracleEvaluationRequest;
import pl.edu.pw.thesis.experiment.oracle.OracleResult;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.config.UnsafeEnvironmentException;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.fault.FaultArmRequest;
import pl.edu.pw.thesis.experiment.runner.fault.FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.FaultVerifyResult;
import pl.edu.pw.thesis.experiment.runner.model.ExclusionCategory;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.OracleSummary;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.reset.StateReset;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;
import pl.edu.pw.thesis.experiment.runner.variant.ExecutionHandle;
import pl.edu.pw.thesis.experiment.runner.variant.VariantAdapter;
import pl.edu.pw.thesis.experiment.runner.variant.WorkflowStopInfo;

/**
 * Phase-1 / 1.1 trial lifecycle: reset → arm F0 → start A → await/terminate (R6) → quiesce →
 * full T_observe (R14) → Oracle once → durable persist (OWN-P1).
 */
public final class TrialRunner {

    private final ExperimentDefinition experiment;
    private final RunnerEnvironment environment;
    private final StateReset stateReset;
    private final FaultController faultController;
    private final VariantAdapter variantAdapter;
    private final IndependentOracle oracle;
    private final FileResultStore resultStore;
    private final RunnerClock clock;

    /** When true after WORKFLOW_NOT_QUIESCED, further trials should be blocked by the caller. */
    private boolean blockFurtherTrials;

    /** Operator interrupt observed — CLI should write experiment-level ABORTED_EXPERIMENT. */
    private boolean operatorInterrupted;

    public TrialRunner(
            ExperimentDefinition experiment,
            RunnerEnvironment environment,
            StateReset stateReset,
            FaultController faultController,
            VariantAdapter variantAdapter,
            IndependentOracle oracle,
            FileResultStore resultStore,
            RunnerClock clock) {
        this.experiment = Objects.requireNonNull(experiment);
        this.environment = Objects.requireNonNull(environment);
        this.stateReset = Objects.requireNonNull(stateReset);
        this.faultController = Objects.requireNonNull(faultController);
        this.variantAdapter = Objects.requireNonNull(variantAdapter);
        this.oracle = Objects.requireNonNull(oracle);
        this.resultStore = Objects.requireNonNull(resultStore);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean isFurtherTrialsBlocked() {
        return blockFurtherTrials;
    }

    public boolean wasOperatorInterrupted() {
        return operatorInterrupted;
    }

    public TrialRecord run(TrialSpec spec) {
        Instant t0 = clock.now();
        ArmedFaultRecord armed = null;
        try {
            if (blockFurtherTrials) {
                return persistExcluded(
                        spec, t0, "PREVIOUS_TRIAL_BLOCKED", "Previous trial required block");
            }
            if (resultStore.hasTerminalArtifact(spec.trialId())) {
                return persistExcluded(
                        spec, t0, "DUPLICATE_TRIAL_ATTEMPT", "Terminal artifact already exists");
            }

            environment.assertSafeForDestructiveOps();
            var baseline = stateReset.resetToProtocolBaseline(spec.businessInputs().sku());

            armed = faultController.arm(FaultArmRequest.from(spec));
            Instant faultTriggeredAt = armed.armedAt();
            FaultVerifyResult verify = faultController.verify(armed, spec);
            if (!verify.ok()) {
                return persistExcluded(
                        spec,
                        t0,
                        "FAULT_VERIFY_FAILED",
                        verify.detail(),
                        faultTriggeredAt,
                        armed);
            }

            ExecutionHandle handle = variantAdapter.start(spec, armed);
            Instant deadline = t0.plus(environment.timing().tRunMax());
            WorkflowStopInfo stop = variantAdapter.awaitStop(handle, deadline);

            TerminatedBy terminatedBy;
            if (stop.terminal()) {
                terminatedBy = TerminatedBy.WORKFLOW_STOP;
            } else {
                // C: re-describe before terminate — natural completion wins the race
                WorkflowStopInfo latest = variantAdapter.describe(handle);
                if (latest.terminal()) {
                    stop = latest;
                    terminatedBy = TerminatedBy.WORKFLOW_STOP;
                } else {
                    try {
                        // R6: Temporal terminate — not cancel; not business compensation
                        variantAdapter.terminate(handle, "T_run_max");
                        terminatedBy = TerminatedBy.T_RUN_MAX;
                    } catch (RuntimeException terminateError) {
                        WorkflowStopInfo after = variantAdapter.describe(handle);
                        if (after.terminal()) {
                            stop = after;
                            terminatedBy = TerminatedBy.WORKFLOW_STOP;
                        } else {
                            return persistExcluded(
                                    spec,
                                    t0,
                                    "TEMPORAL_TERMINATE_FAILED",
                                    terminateError.getMessage(),
                                    faultTriggeredAt,
                                    armed,
                                    handle,
                                    after,
                                    null,
                                    baseline.onHand());
                        }
                    }
                }
            }

            Instant quiesceDeadline = clock.now().plus(environment.timing().quiesceWait());
            WorkflowStopInfo quiesced = variantAdapter.awaitStop(handle, quiesceDeadline);
            if (!quiesced.terminal()) {
                blockFurtherTrials = true;
                return persistExcluded(
                        spec,
                        t0,
                        "WORKFLOW_NOT_QUIESCED",
                        "Temporal execution still not terminal after quiesce wait"
                                + " (Temporal quiescence ≠ service-side drain)",
                        faultTriggeredAt,
                        armed,
                        handle,
                        quiesced,
                        terminatedBy,
                        baseline.onHand());
            }

            Instant tWorkflowStop = quiesced.observedAt();
            clock.sleep(environment.timing().tObserve());
            Instant tTerminal = clock.now();

            final OracleResult oracleResult;
            try {
                oracleResult = oracle.evaluate(new OracleEvaluationRequest(
                        spec.orderId(),
                        spec.businessInputs().sku(),
                        handle.workflowId(),
                        spec.trialId()));
            } catch (SQLException e) {
                return persistExcluded(
                        spec,
                        t0,
                        "ORACLE_FAILURE",
                        e.getMessage(),
                        faultTriggeredAt,
                        armed,
                        handle,
                        quiesced,
                        terminatedBy,
                        baseline.onHand());
            }
            Instant tOracle = oracleResult.evaluatedAt();

            TrialRecord completedCandidate = new TrialRecord(
                    TrialRecord.SCHEMA_VERSION,
                    spec.experimentId(),
                    spec.trialId(),
                    experiment.protocolId(),
                    experiment.phase(),
                    spec.variant(),
                    spec.faultProfileId(),
                    spec.repetitionIndex(),
                    spec.orderId(),
                    spec.businessInputs(),
                    experiment.gitSha(),
                    t0,
                    faultTriggeredAt,
                    null,
                    tWorkflowStop,
                    tTerminal,
                    tOracle,
                    terminatedBy,
                    true,
                    handle.workflowId(),
                    handle.runId(),
                    quiesced.temporalStatus(),
                    HarnessStatus.COMPLETED,
                    true,
                    null,
                    null,
                    OracleSummary.from(oracleResult),
                    quiesced.workflowOutcome(),
                    armed,
                    baseline.onHand(),
                    null);

            return persistCompletedOwnP1(completedCandidate);
        } catch (UnsafeEnvironmentException e) {
            return persistExcluded(spec, t0, e.exclusionReason(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            operatorInterrupted = true;
            return persistExcluded(spec, t0, "HARNESS_CRASH", "Interrupted: " + e.getMessage());
        } catch (SQLException e) {
            return persistExcluded(spec, t0, "SERVICE_UNAVAILABLE", e.getMessage());
        } catch (Exception e) {
            return persistExcluded(spec, t0, "HARNESS_CRASH", e.getMessage());
        } finally {
            if (armed != null) {
                try {
                    faultController.disarm(spec.trialId());
                } catch (Exception ignored) {
                    blockFurtherTrials = true;
                }
            }
        }
    }

    /**
     * OWN-P1: COMPLETED only after durable JSON; on failure keep oracle, write .partial,
     * durable EXCLUDED/PERSISTENCE_FAILURE — never re-run workflow/Oracle.
     */
    private TrialRecord persistCompletedOwnP1(TrialRecord completedCandidate) {
        try {
            resultStore.saveTrial(completedCandidate);
            return completedCandidate;
        } catch (IOException persistError) {
            try {
                resultStore.saveOracleConfirmedPartial(completedCandidate);
            } catch (IOException ignored) {
                // best-effort partial
            }
            TrialRecord excluded = withPersistenceFailure(completedCandidate, persistError);
            try {
                resultStore.saveTrial(excluded);
            } catch (IOException second) {
                // Durable EXCLUDED also failed — still return in-memory excluded with oracle
                return withPersistenceFailure(
                        completedCandidate,
                        new IOException(
                                "Durable EXCLUDED also failed: " + second.getMessage(),
                                persistError));
            }
            return excluded;
        }
    }

    private static TrialRecord withPersistenceFailure(TrialRecord completed, IOException error) {
        return new TrialRecord(
                completed.schemaVersion(),
                completed.experimentId(),
                completed.trialId(),
                completed.protocolId(),
                completed.phase(),
                completed.variant(),
                completed.faultProfileId(),
                completed.repetitionIndex(),
                completed.orderId(),
                completed.businessInputs(),
                completed.gitSha(),
                completed.t0(),
                completed.faultTriggeredAt(),
                completed.faultObservedAt(),
                completed.tWorkflowStop(),
                completed.tTerminal(),
                completed.tOracle(),
                completed.terminatedBy(),
                completed.workflowQuiesced(),
                completed.workflowId(),
                completed.runId(),
                completed.temporalStatus(),
                HarnessStatus.EXCLUDED,
                false,
                ExclusionCategory.TECHNICAL,
                "PERSISTENCE_FAILURE",
                completed.oracle(),
                completed.workflowOutcome(),
                completed.faultArmedRecord(),
                completed.baselineOnHand(),
                error.getMessage());
    }

    private TrialRecord persistExcluded(
            TrialSpec spec, Instant t0, String reason, String infraError) {
        return persistExcluded(spec, t0, reason, infraError, null, null, null, null, null, null);
    }

    private TrialRecord persistExcluded(
            TrialSpec spec,
            Instant t0,
            String reason,
            String infraError,
            Instant faultTriggeredAt,
            ArmedFaultRecord armed) {
        return persistExcluded(
                spec, t0, reason, infraError, faultTriggeredAt, armed, null, null, null, null);
    }

    private TrialRecord persistExcluded(
            TrialSpec spec,
            Instant t0,
            String reason,
            String infraError,
            Instant faultTriggeredAt,
            ArmedFaultRecord armed,
            ExecutionHandle handle,
            WorkflowStopInfo stop,
            TerminatedBy terminatedBy,
            Integer baselineOnHand) {
        TrialRecord record = new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                spec.experimentId(),
                spec.trialId(),
                experiment.protocolId(),
                experiment.phase(),
                spec.variant(),
                spec.faultProfileId(),
                spec.repetitionIndex(),
                spec.orderId(),
                spec.businessInputs(),
                experiment.gitSha(),
                t0,
                faultTriggeredAt,
                null,
                stop == null ? null : stop.observedAt(),
                null,
                null,
                terminatedBy,
                false,
                handle == null ? spec.workflowId() : handle.workflowId(),
                handle == null ? null : handle.runId(),
                stop == null ? null : stop.temporalStatus(),
                HarnessStatus.EXCLUDED,
                false,
                ExclusionCategory.TECHNICAL,
                reason,
                null,
                stop == null ? null : stop.workflowOutcome(),
                armed,
                baselineOnHand,
                infraError);
        try {
            resultStore.saveTrial(record);
        } catch (IOException e) {
            return new TrialRecord(
                    record.schemaVersion(),
                    record.experimentId(),
                    record.trialId(),
                    record.protocolId(),
                    record.phase(),
                    record.variant(),
                    record.faultProfileId(),
                    record.repetitionIndex(),
                    record.orderId(),
                    record.businessInputs(),
                    record.gitSha(),
                    record.t0(),
                    record.faultTriggeredAt(),
                    record.faultObservedAt(),
                    record.tWorkflowStop(),
                    record.tTerminal(),
                    record.tOracle(),
                    record.terminatedBy(),
                    record.workflowQuiesced(),
                    record.workflowId(),
                    record.runId(),
                    record.temporalStatus(),
                    HarnessStatus.EXCLUDED,
                    false,
                    ExclusionCategory.TECHNICAL,
                    "PERSISTENCE_FAILURE",
                    null,
                    record.workflowOutcome(),
                    record.faultArmedRecord(),
                    record.baselineOnHand(),
                    "Persist failed after exclusion: " + e.getMessage()
                            + "; original=" + reason);
        }
        return record;
    }
}
