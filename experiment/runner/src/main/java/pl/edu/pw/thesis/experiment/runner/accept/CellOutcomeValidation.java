package pl.edu.pw.thesis.experiment.runner.accept;

import java.util.Objects;
import java.util.Optional;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/** Result of comparing harness expected outcome to a durable trial record (S4-B). */
public record CellOutcomeValidation(
        String trialId,
        String faultProfileId,
        boolean matched,
        String detail,
        HarnessStatus harnessStatus) {

    public CellOutcomeValidation {
        Objects.requireNonNull(trialId, "trialId");
        Objects.requireNonNull(faultProfileId, "faultProfileId");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(harnessStatus, "harnessStatus");
    }

    public static CellOutcomeValidation evaluate(TrialRecord record) {
        Objects.requireNonNull(record, "record");
        ExpectedCellOutcome expected = ExpectedCellOutcome.forProfile(record.faultProfileId());
        if (record.harnessStatus() != HarnessStatus.COMPLETED) {
            return new CellOutcomeValidation(
                    record.trialId(),
                    record.faultProfileId(),
                    false,
                    "harnessStatus="
                            + record.harnessStatus()
                            + " exclusion="
                            + record.exclusionReason()
                            + " (not COMPLETED; cannot count as successful cell)",
                    record.harnessStatus());
        }
        if (record.oracle() == null) {
            return new CellOutcomeValidation(
                    record.trialId(),
                    record.faultProfileId(),
                    false,
                    "COMPLETED trial missing Oracle summary",
                    record.harnessStatus());
        }
        String actual = record.oracle().classification();
        String want = expected.expectedClassification().name();
        if (!want.equals(actual)) {
            return new CellOutcomeValidation(
                    record.trialId(),
                    record.faultProfileId(),
                    false,
                    "expectedClassification=" + want + " actual=" + actual,
                    record.harnessStatus());
        }
        // F3-Unresolved gate already applied inside TrialRunner before COMPLETED; reaching here means
        // gate passed when required.
        return new CellOutcomeValidation(
                record.trialId(),
                record.faultProfileId(),
                true,
                "matched expectedClassification=" + want,
                record.harnessStatus());
    }

    public Optional<String> mismatchDetail() {
        return matched ? Optional.empty() : Optional.of(detail);
    }
}
