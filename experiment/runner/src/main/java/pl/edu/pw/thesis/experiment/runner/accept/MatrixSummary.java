package pl.edu.pw.thesis.experiment.runner.accept;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/**
 * Aggregated matrix report for one experiment (S4-B).
 *
 * <p>{@code plannedCells} comes from the experiment definition (profiles × n), not from the number
 * of executed {@link TrialRecord}s — interrupted runs must not hide never-executed cells.
 */
public record MatrixSummary(
        String experimentId,
        String protocolId,
        String gitSha,
        Instant generatedAt,
        int plannedCells,
        int executedCells,
        int matched,
        int mismatched,
        int excluded,
        int notExecuted,
        List<CellOutcomeValidation> cells,
        boolean allMatched) {

    public MatrixSummary {
        Objects.requireNonNull(experimentId, "experimentId");
        Objects.requireNonNull(protocolId, "protocolId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        cells = List.copyOf(cells);
        if (plannedCells < 0
                || executedCells < 0
                || matched < 0
                || mismatched < 0
                || excluded < 0
                || notExecuted < 0) {
            throw new IllegalArgumentException("matrix counts must be non-negative");
        }
    }

    /**
     * @param plannedCells expected cells = {@code faultProfileIds().size() * n()} from the
     *     experiment definition (never inferred from {@code trials.size()})
     * @param trials executed/observed records only — never synthesize missing cells as success
     */
    public static MatrixSummary fromTrials(
            String experimentId,
            String protocolId,
            String gitSha,
            Instant generatedAt,
            int plannedCells,
            List<TrialRecord> trials) {
        if (plannedCells < 0) {
            throw new IllegalArgumentException("plannedCells must be non-negative");
        }
        Objects.requireNonNull(trials, "trials");
        List<CellOutcomeValidation> cells = new ArrayList<>();
        int matched = 0;
        int mismatched = 0;
        int excluded = 0;
        for (TrialRecord trial : trials) {
            CellOutcomeValidation v = CellOutcomeValidation.evaluate(trial);
            cells.add(v);
            if (v.matched()) {
                matched++;
            } else if (isExclusion(v)) {
                excluded++;
            } else {
                mismatched++;
            }
        }
        int executed = trials.size();
        int notExecuted = Math.max(0, plannedCells - executed);
        boolean allMatched =
                plannedCells > 0
                        && matched == plannedCells
                        && mismatched == 0
                        && excluded == 0
                        && notExecuted == 0;
        return new MatrixSummary(
                experimentId,
                protocolId,
                gitSha == null ? "unknown" : gitSha,
                generatedAt,
                plannedCells,
                executed,
                matched,
                mismatched,
                excluded,
                notExecuted,
                cells,
                allMatched);
    }

    private static boolean isExclusion(CellOutcomeValidation v) {
        return v.harnessStatus() != HarnessStatus.COMPLETED;
    }

    /** CLI: 0 = all matched; 1 = harness/exclusion/incomplete/empty; 3 = classification mismatch. */
    public int suggestedExitCode() {
        if (plannedCells == 0 || notExecuted > 0 || excluded > 0) {
            return 1;
        }
        if (allMatched) {
            return 0;
        }
        if (mismatched > 0) {
            boolean anyMismatchClassification = cells.stream()
                    .anyMatch(c -> !c.matched()
                            && c.detail().startsWith("expectedClassification="));
            if (anyMismatchClassification) {
                return 3;
            }
        }
        return 1;
    }
}
