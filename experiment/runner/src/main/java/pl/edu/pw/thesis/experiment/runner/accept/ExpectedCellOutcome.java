package pl.edu.pw.thesis.experiment.runner.accept;

import java.util.Objects;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.experiment.runner.fault.F0FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F1PFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F2FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F3RecoveredFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F3UnresolvedFaultController;

/**
 * Harness-level expected cell outcome (S4-B). Independent of Oracle classifier internals.
 *
 * <p>Owner shorthand <strong>SUCCESS</strong> maps to Oracle {@link BusinessOutcome#CONFIRMED_SUCCESS}.
 */
public record ExpectedCellOutcome(
        String faultProfileId, BusinessOutcome expectedClassification, boolean requireF3UnresolvedGate) {

    public ExpectedCellOutcome {
        Objects.requireNonNull(faultProfileId, "faultProfileId");
        Objects.requireNonNull(expectedClassification, "expectedClassification");
    }

    public static ExpectedCellOutcome forProfile(String faultProfileId) {
        return switch (faultProfileId) {
            case F0FaultController.PROFILE_F0, F3RecoveredFaultController.PROFILE ->
                    new ExpectedCellOutcome(faultProfileId, BusinessOutcome.CONFIRMED_SUCCESS, false);
            case F1PFaultController.PROFILE_F1_P, F2FaultController.PROFILE_F2 ->
                    new ExpectedCellOutcome(faultProfileId, BusinessOutcome.CONFIRMED_FAILURE, false);
            case F3UnresolvedFaultController.PROFILE ->
                    new ExpectedCellOutcome(faultProfileId, BusinessOutcome.AMBIGUOUS, true);
            default -> throw new IllegalArgumentException("no expected outcome for " + faultProfileId);
        };
    }
}
