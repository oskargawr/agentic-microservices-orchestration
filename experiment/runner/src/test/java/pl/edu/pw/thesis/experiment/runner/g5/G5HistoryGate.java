package pl.edu.pw.thesis.experiment.runner.g5;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * G5-3 history acceptance gate (D-AUTH-G5 / discovery).
 *
 * <p>Disqualifying condition: any {@link EventType#EVENT_TYPE_WORKFLOW_TASK_STARTED} on the measured
 * execution with {@code eventId} strictly less than the corresponding {@link
 * EventType#EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED} eventId. Does not use client-side terminate()
 * wall-clock.
 */
public final class G5HistoryGate {

    private G5HistoryGate() {}

    public static G5HistoryEventKind mapKind(EventType type) {
        if (type == EventType.EVENT_TYPE_WORKFLOW_TASK_STARTED) {
            return G5HistoryEventKind.WORKFLOW_TASK_STARTED;
        }
        if (type == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED) {
            return G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED;
        }
        return G5HistoryEventKind.OTHER;
    }

    public static List<G5HistoryEventView> fromTemporalEvents(List<HistoryEvent> events) {
        Objects.requireNonNull(events, "events");
        List<G5HistoryEventView> views = new ArrayList<>(events.size());
        for (HistoryEvent e : events) {
            views.add(new G5HistoryEventView(e.getEventId(), mapKind(e.getEventType())));
        }
        return views;
    }

    public static G5HistoryValidationResult validateTemporalHistory(List<HistoryEvent> events) {
        if (events == null) {
            return G5HistoryValidationResult.incomplete("history events list is null");
        }
        return validate(fromTemporalEvents(events));
    }

    /**
     * Validates a paginated fetch. Incomplete retrieval fail-closes regardless of partial events.
     * Callers must pass the fetch for the exact measured {@code workflowId}/{@code runId}.
     */
    public static G5HistoryValidationResult validateFetch(G5HistoryFetchResult fetch) {
        if (fetch == null) {
            return G5HistoryValidationResult.incomplete("history fetch result is null");
        }
        if (!fetch.complete()) {
            String reason = fetch.failureReason().isBlank()
                    ? "history retrieval incomplete"
                    : fetch.failureReason();
            return G5HistoryValidationResult.incomplete(reason);
        }
        return validateTemporalHistory(fetch.events());
    }

    public static G5HistoryValidationResult validate(List<G5HistoryEventView> events) {
        if (events == null) {
            return G5HistoryValidationResult.incomplete("history event views list is null");
        }
        if (events.isEmpty()) {
            return G5HistoryValidationResult.incomplete("history is empty");
        }

        List<G5HistoryEventView> ordered =
                events.stream().sorted(Comparator.comparingLong(G5HistoryEventView::eventId)).toList();

        Optional<G5HistoryEventView> termination = ordered.stream()
                .filter(e -> e.kind() == G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)
                .findFirst();
        if (termination.isEmpty()) {
            return new G5HistoryValidationResult(
                    false,
                    false,
                    false,
                    false,
                    Optional.empty(),
                    List.of(),
                    "WorkflowExecutionTerminated event not found");
        }

        long termId = termination.get().eventId();
        List<Long> earlyStarts = ordered.stream()
                .filter(e -> e.kind() == G5HistoryEventKind.WORKFLOW_TASK_STARTED)
                .filter(e -> e.eventId() < termId)
                .map(G5HistoryEventView::eventId)
                .toList();

        boolean early = !earlyStarts.isEmpty();
        boolean orderingValid = !early;
        String detail = early
                ? "WorkflowTaskStarted eventId(s) before WorkflowExecutionTerminated eventId="
                        + termId
                        + ": "
                        + earlyStarts
                : "no WorkflowTaskStarted before WorkflowExecutionTerminated eventId=" + termId;

        return new G5HistoryValidationResult(
                true, true, early, orderingValid, Optional.of(termId), earlyStarts, detail);
    }
}
