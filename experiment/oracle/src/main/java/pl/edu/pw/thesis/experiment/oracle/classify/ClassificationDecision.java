package pl.edu.pw.thesis.experiment.oracle.classify;

import java.util.List;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.experiment.oracle.OraclePattern;
import pl.edu.pw.thesis.experiment.oracle.OracleReasonCode;

public record ClassificationDecision(
        BusinessOutcome classification,
        OraclePattern pattern,
        OracleReasonCode reasonCode,
        List<String> violatedInvariants,
        List<String> warnings) {

    public ClassificationDecision {
        violatedInvariants = List.copyOf(violatedInvariants);
        warnings = List.copyOf(warnings);
    }
}
