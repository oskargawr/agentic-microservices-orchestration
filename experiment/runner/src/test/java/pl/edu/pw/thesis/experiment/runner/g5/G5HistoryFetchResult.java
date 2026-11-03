package pl.edu.pw.thesis.experiment.runner.g5;

import io.temporal.api.history.v1.HistoryEvent;
import java.util.List;
import java.util.Objects;

/** Outcome of a paginated Temporal history fetch for an exact workflowId/runId. */
public record G5HistoryFetchResult(
        String workflowId,
        String runId,
        List<HistoryEvent> events,
        int pageCount,
        boolean complete,
        String failureReason) {

    public G5HistoryFetchResult {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(runId, "runId");
        events = events == null ? List.of() : List.copyOf(events);
        failureReason = failureReason == null ? "" : failureReason;
    }

    public static G5HistoryFetchResult incomplete(
            String workflowId, String runId, List<HistoryEvent> partial, int pageCount, String reason) {
        return new G5HistoryFetchResult(workflowId, runId, partial, pageCount, false, reason);
    }

    public static G5HistoryFetchResult complete(
            String workflowId, String runId, List<HistoryEvent> events, int pageCount) {
        return new G5HistoryFetchResult(workflowId, runId, events, pageCount, true, "");
    }
}
