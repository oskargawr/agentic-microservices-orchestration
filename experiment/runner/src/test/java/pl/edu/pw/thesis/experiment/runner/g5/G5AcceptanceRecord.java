package pl.edu.pw.thesis.experiment.runner.g5;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Durable G5-3 acceptance artifact (separate from {@code TrialRecord}). */
public record G5AcceptanceRecord(
        String scenarioId,
        String workflowId,
        String runId,
        String gitSha,
        String preflightResult,
        String isolationDiagnostics,
        String historySource,
        String historyArtifactPath,
        boolean historyComplete,
        boolean workflowExecutionTerminatedFound,
        boolean workflowTaskStartedBeforeTermination,
        String eventOrderingValidation,
        Long terminationEventId,
        List<Long> workflowTaskStartedEventIdsBeforeTermination,
        String acceptanceResult,
        String invalidationReason,
        Instant evaluatedAt) {

    public static final String SCENARIO_G5_3 = "G5-3";
    public static final String VALID = "VALID";
    public static final String INVALID = "INVALID";

    public G5AcceptanceRecord {
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(gitSha, "gitSha");
        Objects.requireNonNull(preflightResult, "preflightResult");
        Objects.requireNonNull(isolationDiagnostics, "isolationDiagnostics");
        Objects.requireNonNull(historySource, "historySource");
        Objects.requireNonNull(historyArtifactPath, "historyArtifactPath");
        Objects.requireNonNull(eventOrderingValidation, "eventOrderingValidation");
        workflowTaskStartedEventIdsBeforeTermination =
                workflowTaskStartedEventIdsBeforeTermination == null
                        ? List.of()
                        : List.copyOf(workflowTaskStartedEventIdsBeforeTermination);
        Objects.requireNonNull(acceptanceResult, "acceptanceResult");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (!VALID.equals(acceptanceResult) && !INVALID.equals(acceptanceResult)) {
            throw new IllegalArgumentException("acceptanceResult must be VALID or INVALID");
        }
    }

    public static G5AcceptanceRecord fromValidation(
            String workflowId,
            String runId,
            String gitSha,
            String preflightResult,
            String isolationDiagnostics,
            String historySource,
            String historyArtifactPath,
            G5HistoryValidationResult validation,
            Instant evaluatedAt) {
        boolean valid = "PASS".equalsIgnoreCase(preflightResult) && validation.passesAcceptanceGate();
        String reason = valid
                ? null
                : (!"PASS".equalsIgnoreCase(preflightResult)
                        ? "preflight=" + preflightResult
                        : validation.detail());
        return new G5AcceptanceRecord(
                SCENARIO_G5_3,
                workflowId,
                runId,
                gitSha,
                preflightResult,
                isolationDiagnostics,
                historySource,
                historyArtifactPath,
                validation.historyComplete(),
                validation.workflowExecutionTerminatedFound(),
                validation.workflowTaskStartedBeforeTermination(),
                validation.passesAcceptanceGate() ? "PASS" : "FAIL",
                validation.terminationEventId().orElse(null),
                validation.workflowTaskStartedEventIdsBeforeTermination(),
                valid ? VALID : INVALID,
                reason,
                evaluatedAt);
    }

    /** G5-3 test gate: call only after durable acceptance JSON is written. */
    public void requireValidForG5Evidence() {
        if (!VALID.equals(acceptanceResult)) {
            throw new AssertionError(
                    "G5-3 acceptance INVALID after durable artifact write: " + invalidationReason);
        }
        if (!"PASS".equalsIgnoreCase(preflightResult)) {
            throw new AssertionError("G5-3 requires preflight PASS, got " + preflightResult);
        }
        if (!historyComplete) {
            throw new AssertionError("G5-3 requires complete history");
        }
        if (!workflowExecutionTerminatedFound) {
            throw new AssertionError("G5-3 requires WorkflowExecutionTerminated");
        }
        if (workflowTaskStartedBeforeTermination) {
            throw new AssertionError("G5-3 requires no WorkflowTaskStarted before termination");
        }
    }
}
