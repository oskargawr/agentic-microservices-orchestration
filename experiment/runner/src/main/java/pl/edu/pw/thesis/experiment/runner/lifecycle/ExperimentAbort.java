package pl.edu.pw.thesis.experiment.runner.lifecycle;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentStatusRecord;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;

/**
 * OWN-P3: experiment-level abort. Never rewrites terminal trial artifacts —
 * only writes separate {@code experiment-status.json}.
 */
public final class ExperimentAbort {

    private ExperimentAbort() {}

    /**
     * Persists {@link HarnessStatus#ABORTED_EXPERIMENT} at experiment level if no
     * experiment status is already terminal ({@code COMPLETED} / {@code ABORTED_EXPERIMENT}).
     */
    public static ExperimentStatusRecord abortExperiment(
            FileResultStore store, String experimentId, String reason, Instant at)
            throws IOException {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(experimentId, "experimentId");
        ExperimentStatusRecord existing = store.loadExperimentStatus();
        if (existing != null
                && (existing.harnessStatus() == HarnessStatus.COMPLETED
                        || existing.harnessStatus() == HarnessStatus.ABORTED_EXPERIMENT)) {
            return existing;
        }
        ExperimentStatusRecord aborted =
                ExperimentStatusRecord.aborted(experimentId, reason, at);
        store.saveExperimentStatus(aborted);
        return aborted;
    }
}
