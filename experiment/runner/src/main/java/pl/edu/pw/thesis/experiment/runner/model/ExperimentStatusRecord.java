package pl.edu.pw.thesis.experiment.runner.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Experiment-level status artifact (OWN-P3) — separate from per-trial {@link TrialRecord}.
 * Does not rewrite terminal trial harness statuses.
 */
public record ExperimentStatusRecord(
        String experimentId,
        HarnessStatus harnessStatus,
        String reason,
        Instant updatedAt) {

    public ExperimentStatusRecord {
        Objects.requireNonNull(experimentId, "experimentId");
        Objects.requireNonNull(harnessStatus, "harnessStatus");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (harnessStatus != HarnessStatus.ABORTED_EXPERIMENT
                && harnessStatus != HarnessStatus.COMPLETED
                && harnessStatus != HarnessStatus.EXCLUDED) {
            throw new IllegalArgumentException("unsupported experiment harnessStatus");
        }
    }

    public static ExperimentStatusRecord aborted(String experimentId, String reason, Instant at) {
        return new ExperimentStatusRecord(
                experimentId, HarnessStatus.ABORTED_EXPERIMENT, reason, at);
    }
}
