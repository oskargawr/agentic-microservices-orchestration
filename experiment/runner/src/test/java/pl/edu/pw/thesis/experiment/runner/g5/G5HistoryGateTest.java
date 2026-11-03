package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class G5HistoryGateTest {

    @Test
    void mapsSdkEventTypes() {
        assertThat(G5HistoryGate.mapKind(EventType.EVENT_TYPE_WORKFLOW_TASK_STARTED))
                .isEqualTo(G5HistoryEventKind.WORKFLOW_TASK_STARTED);
        assertThat(G5HistoryGate.mapKind(EventType.EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED))
                .isEqualTo(G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED);
        assertThat(G5HistoryGate.mapKind(EventType.EVENT_TYPE_WORKFLOW_TASK_SCHEDULED))
                .isEqualTo(G5HistoryEventKind.OTHER);
    }

    @Test
    void valid_whenTerminationPresentAndNoEarlierWorkflowTaskStarted() {
        G5HistoryValidationResult r = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(1, G5HistoryEventKind.OTHER),
                new G5HistoryEventView(5, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));
        assertThat(r.passesAcceptanceGate()).isTrue();
        assertThat(r.workflowExecutionTerminatedFound()).isTrue();
        assertThat(r.workflowTaskStartedBeforeTermination()).isFalse();
        assertThat(r.terminationEventId()).contains(5L);
    }

    @Test
    void invalid_whenWorkflowTaskStartedBeforeTermination_byEventId() {
        G5HistoryValidationResult r = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(3, G5HistoryEventKind.WORKFLOW_TASK_STARTED),
                new G5HistoryEventView(10, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));
        assertThat(r.passesAcceptanceGate()).isFalse();
        assertThat(r.workflowTaskStartedBeforeTermination()).isTrue();
        assertThat(r.workflowTaskStartedEventIdsBeforeTermination()).containsExactly(3L);
    }

    @Test
    void invalid_whenTerminationMissing() {
        G5HistoryValidationResult r = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(1, G5HistoryEventKind.OTHER),
                new G5HistoryEventView(2, G5HistoryEventKind.WORKFLOW_TASK_STARTED)));
        assertThat(r.passesAcceptanceGate()).isFalse();
        assertThat(r.workflowExecutionTerminatedFound()).isFalse();
        assertThat(r.historyComplete()).isFalse();
    }

    @Test
    void invalid_whenHistoryEmptyOrNull() {
        assertThat(G5HistoryGate.validate(List.of()).passesAcceptanceGate()).isFalse();
        assertThat(G5HistoryGate.validate(null).passesAcceptanceGate()).isFalse();
        assertThat(G5HistoryGate.validateTemporalHistory(null).passesAcceptanceGate()).isFalse();
    }

    @Test
    void workflowTaskStartedAfterTermination_doesNotDisqualify() {
        // Ordering uses eventId; a start after terminate is not the G5-3 contamination case.
        G5HistoryValidationResult r = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(10, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED),
                new G5HistoryEventView(11, G5HistoryEventKind.WORKFLOW_TASK_STARTED)));
        assertThat(r.passesAcceptanceGate()).isTrue();
        assertThat(r.workflowTaskStartedBeforeTermination()).isFalse();
    }

    @Test
    void validateTemporalHistory_usesSdkEventIdAndTypes() {
        List<HistoryEvent> events = List.of(
                HistoryEvent.newBuilder()
                        .setEventId(1)
                        .setEventType(EventType.EVENT_TYPE_WORKFLOW_TASK_SCHEDULED)
                        .build(),
                HistoryEvent.newBuilder()
                        .setEventId(2)
                        .setEventType(EventType.EVENT_TYPE_WORKFLOW_TASK_STARTED)
                        .build(),
                HistoryEvent.newBuilder()
                        .setEventId(9)
                        .setEventType(EventType.EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED)
                        .build());
        G5HistoryValidationResult r = G5HistoryGate.validateTemporalHistory(events);
        assertThat(r.passesAcceptanceGate()).isFalse();
        assertThat(r.terminationEventId()).contains(9L);
        assertThat(r.workflowTaskStartedEventIdsBeforeTermination()).containsExactly(2L);
    }

    @Test
    void scopesToProvidedEventList_only() {
        // Gate evaluates exactly the supplied history (caller must fetch by workflowId+runId).
        G5HistoryValidationResult otherRun =
                G5HistoryGate.validate(List.of(new G5HistoryEventView(1, G5HistoryEventKind.OTHER)));
        assertThat(otherRun.workflowExecutionTerminatedFound()).isFalse();
    }
}
