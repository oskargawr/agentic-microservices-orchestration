package pl.edu.pw.thesis.experiment.runner.accept;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/** Aggregated matrix report for one experiment (S4-B). */
public record MatrixSummary(
        String experimentId,
        String protocolId,
        String gitSha,
        Instant generatedAt,
        int plannedCells,
        int completedMatched,
        int mismatchedOrExcluded,
        List<CellOutcomeValidation> cells,
        boolean allMatched) {

    public MatrixSummary {
        Objects.requireNonNull(experimentId, "experimentId");
        Objects.requireNonNull(protocolId, "protocolId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        cells = List.copyOf(cells);
    }

    public static MatrixSummary fromTrials(
            String experimentId,
            String protocolId,
            String gitSha,
            Instant generatedAt,
            List<TrialRecord> trials) {
        List<CellOutcomeValidation> cells = new ArrayList<>();
        int matched = 0;
        for (TrialRecord trial : trials) {
            CellOutcomeValidation v = CellOutcomeValidation.evaluate(trial);
            cells.add(v);
            if (v.matched()) {
                matched++;
            }
        }
        int planned = trials.size();
        int bad = planned - matched;
        return new MatrixSummary(
                experimentId,
                protocolId,
                gitSha == null ? "unknown" : gitSha,
                generatedAt,
                planned,
                matched,
                bad,
                cells,
                bad == 0 && planned > 0);
    }

    /** CLI: 0 = all matched; 1 = harness/exclusion issues or empty; 3 = classification mismatch. */
    public int suggestedExitCode() {
        if (plannedCells == 0) {
            return 1;
        }
        if (allMatched) {
            return 0;
        }
        boolean anyMismatchClassification = cells.stream()
                .anyMatch(c -> !c.matched()
                        && c.detail().startsWith("expectedClassification="));
        if (anyMismatchClassification) {
            return 3;
        }
        return 1;
    }
}
