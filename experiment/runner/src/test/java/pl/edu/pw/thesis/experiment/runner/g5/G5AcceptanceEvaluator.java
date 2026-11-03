package pl.edu.pw.thesis.experiment.runner.g5;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/**
 * G5-3 acceptance evaluation: preflight + TrialRecord outcome + durable JSON + Oracle once +
 * paginated history. Does not mutate {@link TrialRecord}.
 */
public final class G5AcceptanceEvaluator {

    private G5AcceptanceEvaluator() {}

    public static G5AcceptanceRecord evaluate(
            TrialRecord measured,
            Path durableTrialJson,
            TrialRecord durableRead,
            int oracleEvaluateCalls,
            String preflightResult,
            String isolationDiagnostics,
            String historySource,
            String historyArtifactPath,
            G5HistoryValidationResult validation,
            Instant evaluatedAt) {
        Objects.requireNonNull(preflightResult, "preflightResult");
        Objects.requireNonNull(isolationDiagnostics, "isolationDiagnostics");
        Objects.requireNonNull(historySource, "historySource");
        Objects.requireNonNull(historyArtifactPath, "historyArtifactPath");
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");

        List<String> failures = new ArrayList<>();

        if (!"PASS".equalsIgnoreCase(preflightResult)) {
            failures.add("preflight=" + preflightResult);
        }
        if (!validation.passesAcceptanceGate()) {
            failures.add(
                    validation.detail().isBlank() ? "history gate failed" : validation.detail());
        }

        TrialOutcomeCheck trial = evaluateTrialOutcome(
                measured, durableTrialJson, durableRead, oracleEvaluateCalls);
        failures.addAll(trial.failures());

        boolean valid = failures.isEmpty();
        String workflowId = measured != null && measured.workflowId() != null
                ? measured.workflowId()
                : "unknown";
        String runId = measured != null && measured.runId() != null && !measured.runId().isBlank()
                ? measured.runId()
                : "unknown";
        String gitSha = measured != null && measured.gitSha() != null ? measured.gitSha() : "unknown";

        return new G5AcceptanceRecord(
                G5AcceptanceRecord.SCENARIO_G5_3,
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
                trial.harnessStatus(),
                trial.workflowQuiesced(),
                trial.temporalStatus(),
                trial.terminatedBy(),
                trial.oraclePresent(),
                oracleEvaluateCalls,
                trial.durableJsonPresent(),
                trial.durableMatchesRunner(),
                trial.outcomeValidation(),
                valid ? G5AcceptanceRecord.VALID : G5AcceptanceRecord.INVALID,
                valid ? null : String.join("; ", failures),
                evaluatedAt);
    }

    public static TrialOutcomeCheck evaluateTrialOutcome(
            TrialRecord measured,
            Path durableTrialJson,
            TrialRecord durableRead,
            int oracleEvaluateCalls) {
        List<String> failures = new ArrayList<>();
        String harnessStatus = measured == null || measured.harnessStatus() == null
                ? "null"
                : measured.harnessStatus().name();
        boolean quiesced = measured != null && measured.workflowQuiesced();
        String temporalStatus = measured == null ? null : measured.temporalStatus();
        String terminatedBy = measured == null || measured.terminatedBy() == null
                ? null
                : measured.terminatedBy().name();
        boolean oraclePresent = measured != null && measured.oracle() != null;
        boolean durablePresent = durableTrialJson != null && Files.isRegularFile(durableTrialJson);
        boolean durableMatches =
                durablePresent && durableRead != null && Objects.equals(measured, durableRead);

        if (measured == null) {
            failures.add("measured TrialRecord is null");
        } else {
            if (measured.harnessStatus() != HarnessStatus.COMPLETED) {
                failures.add("harnessStatus=" + harnessStatus + " (EXCLUDED is not acceptable G5-3)");
            }
            if (!measured.workflowQuiesced()) {
                failures.add("workflowQuiesced=false");
            }
            if (!"TERMINATED".equals(measured.temporalStatus())) {
                failures.add("temporalStatus=" + measured.temporalStatus());
            }
            if (measured.terminatedBy() != TerminatedBy.T_RUN_MAX) {
                failures.add("terminatedBy=" + terminatedBy);
            }
            if (measured.oracle() == null) {
                failures.add("oracle summary missing");
            }
        }
        if (oracleEvaluateCalls != 1) {
            failures.add("oracleEvaluateCalls=" + oracleEvaluateCalls + " (require exactly 1)");
        }
        if (!durablePresent) {
            failures.add("durable TrialRecord JSON missing");
        } else if (durableRead == null) {
            failures.add("durable TrialRecord JSON unreadable");
        } else if (!Objects.equals(measured, durableRead)) {
            failures.add("durable TrialRecord does not equal TrialRunner return value");
        }

        String outcomeValidation = failures.isEmpty() ? "PASS" : "FAIL";
        return new TrialOutcomeCheck(
                harnessStatus,
                quiesced,
                temporalStatus,
                terminatedBy,
                oraclePresent,
                durablePresent,
                durableMatches,
                outcomeValidation,
                List.copyOf(failures));
    }

    public record TrialOutcomeCheck(
            String harnessStatus,
            boolean workflowQuiesced,
            String temporalStatus,
            String terminatedBy,
            boolean oraclePresent,
            boolean durableJsonPresent,
            boolean durableMatchesRunner,
            String outcomeValidation,
            List<String> failures) {}
}
