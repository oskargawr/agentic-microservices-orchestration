package pl.edu.pw.thesis.experiment.runner.model;

import java.time.Instant;
import java.util.List;
import pl.edu.pw.thesis.experiment.oracle.OracleResult;

/** Persistable Oracle fields for trial artifacts (snapshot kept separately when needed). */
public record OracleSummary(
        String classification,
        String pattern,
        String reasonCode,
        List<String> violatedInvariants,
        List<String> warnings,
        String evaluatorVersion,
        Instant evaluatedAt) {

    public static OracleSummary from(OracleResult result) {
        return new OracleSummary(
                result.classification().name(),
                result.pattern().name(),
                result.reasonCode().name(),
                result.violatedInvariants(),
                result.warnings(),
                result.evaluatorVersion(),
                result.evaluatedAt());
    }
}
