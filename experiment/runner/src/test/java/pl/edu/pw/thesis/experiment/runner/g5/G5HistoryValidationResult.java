package pl.edu.pw.thesis.experiment.runner.g5;

import java.util.List;
import java.util.Optional;

/** Outcome of the G5-3 Temporal history event-ordering gate. */
public record G5HistoryValidationResult(
        boolean historyComplete,
        boolean workflowExecutionTerminatedFound,
        boolean workflowTaskStartedBeforeTermination,
        boolean eventOrderingValid,
        Optional<Long> terminationEventId,
        List<Long> workflowTaskStartedEventIdsBeforeTermination,
        String detail) {

    public G5HistoryValidationResult {
        terminationEventId = terminationEventId == null ? Optional.empty() : terminationEventId;
        workflowTaskStartedEventIdsBeforeTermination =
                workflowTaskStartedEventIdsBeforeTermination == null
                        ? List.of()
                        : List.copyOf(workflowTaskStartedEventIdsBeforeTermination);
        detail = detail == null ? "" : detail;
    }

    public boolean passesAcceptanceGate() {
        return historyComplete
                && workflowExecutionTerminatedFound
                && eventOrderingValid
                && !workflowTaskStartedBeforeTermination;
    }

    public static G5HistoryValidationResult incomplete(String detail) {
        return new G5HistoryValidationResult(
                false, false, false, false, Optional.empty(), List.of(), detail);
    }
}
