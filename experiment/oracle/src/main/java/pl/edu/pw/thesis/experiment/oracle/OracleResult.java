package pl.edu.pw.thesis.experiment.oracle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;

public record OracleResult(
        BusinessOutcome classification,
        OraclePattern pattern,
        OracleReasonCode reasonCode,
        List<String> violatedInvariants,
        List<String> warnings,
        BusinessSnapshot snapshot,
        String workflowId,
        String trialId,
        String evaluatorVersion,
        Instant evaluatedAt) {

    public static final String EVALUATOR_VERSION = "th-401-a-1";

    public OracleResult {
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(reasonCode, "reasonCode");
        violatedInvariants = List.copyOf(violatedInvariants);
        warnings = List.copyOf(warnings);
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(evaluatorVersion, "evaluatorVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }
}
