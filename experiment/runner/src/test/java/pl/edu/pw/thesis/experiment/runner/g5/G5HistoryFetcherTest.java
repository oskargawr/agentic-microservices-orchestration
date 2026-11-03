package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.History;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class G5HistoryFetcherTest {

    @Test
    void onePage_emptyNextToken_isComplete() {
        G5HistoryPageClient client = req -> response(
                List.of(event(1, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED)), ByteString.EMPTY);
        G5HistoryFetchResult r = new G5HistoryFetcher(client, "default", 10).fetchAll("wf-1", "run-1");
        assertThat(r.complete()).isTrue();
        assertThat(r.pageCount()).isEqualTo(1);
        assertThat(r.events()).hasSize(1);
        assertThat(r.workflowId()).isEqualTo("wf-1");
        assertThat(r.runId()).isEqualTo("run-1");
    }

    @Test
    void multiplePages_untilEmptyToken() {
        AtomicInteger calls = new AtomicInteger();
        G5HistoryPageClient client = req -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                assertThat(req.getNextPageToken()).isEqualTo(ByteString.EMPTY);
                return response(
                        List.of(event(1, EventType.EVENT_TYPE_WORKFLOW_TASK_SCHEDULED)),
                        ByteString.copyFromUtf8("page2"));
            }
            if (n == 2) {
                assertThat(req.getNextPageToken().toStringUtf8()).isEqualTo("page2");
                return response(
                        List.of(event(2, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED)),
                        ByteString.EMPTY);
            }
            throw new AssertionError("unexpected page " + n);
        };
        G5HistoryFetchResult r = new G5HistoryFetcher(client, "default", 1).fetchAll("wf-x", "run-x");
        assertThat(r.complete()).isTrue();
        assertThat(r.pageCount()).isEqualTo(2);
        assertThat(r.events()).extracting(HistoryEvent::getEventId).containsExactly(1L, 2L);
    }

    @Test
    void emptyHistory_stillCompleteWhenTokenEmpty() {
        G5HistoryPageClient client = req -> response(List.of(), ByteString.EMPTY);
        G5HistoryFetchResult r = new G5HistoryFetcher(client, "default").fetchAll("wf", "run");
        assertThat(r.complete()).isTrue();
        assertThat(r.events()).isEmpty();
        assertThat(G5HistoryGate.validateFetch(r).passesAcceptanceGate()).isFalse();
    }

    @Test
    void apiErrorOnLaterPage_failClosedIncomplete() {
        AtomicInteger calls = new AtomicInteger();
        G5HistoryPageClient client = req -> {
            if (calls.incrementAndGet() == 1) {
                return response(
                        List.of(event(1, EventType.EVENT_TYPE_WORKFLOW_TASK_SCHEDULED)),
                        ByteString.copyFromUtf8("more"));
            }
            throw new IllegalStateException("boom on page 2");
        };
        G5HistoryFetchResult r = new G5HistoryFetcher(client, "default", 1).fetchAll("wf", "run");
        assertThat(r.complete()).isFalse();
        assertThat(r.pageCount()).isEqualTo(1);
        assertThat(r.events()).hasSize(1);
        assertThat(r.failureReason()).contains("boom on page 2");
        assertThat(G5HistoryGate.validateFetch(r).historyComplete()).isFalse();
        assertThat(G5HistoryGate.validateFetch(r).passesAcceptanceGate()).isFalse();
    }

    @Test
    void scopesRequestToExactWorkflowIdAndRunId() {
        List<GetWorkflowExecutionHistoryRequest> seen = new ArrayList<>();
        G5HistoryPageClient client = req -> {
            seen.add(req);
            return response(List.of(), ByteString.EMPTY);
        };
        new G5HistoryFetcher(client, "ns-lab").fetchAll("order-abc", "run-xyz");
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).getNamespace()).isEqualTo("ns-lab");
        assertThat(seen.get(0).getExecution().getWorkflowId()).isEqualTo("order-abc");
        assertThat(seen.get(0).getExecution().getRunId()).isEqualTo("run-xyz");
    }

    @Test
    void blankIds_failClosed() {
        G5HistoryFetchResult r = new G5HistoryFetcher(req -> {
                    throw new AssertionError("must not call API");
                }, "default")
                .fetchAll(" ", "run");
        assertThat(r.complete()).isFalse();
    }

    @Test
    void validateFetch_missingTermination() {
        G5HistoryFetchResult fetch = G5HistoryFetchResult.complete(
                "wf",
                "run",
                List.of(event(1, EventType.EVENT_TYPE_WORKFLOW_TASK_STARTED)),
                1);
        G5HistoryValidationResult v = G5HistoryGate.validateFetch(fetch);
        assertThat(v.workflowExecutionTerminatedFound()).isFalse();
        assertThat(v.passesAcceptanceGate()).isFalse();
    }

    @Test
    void validateFetch_workflowTaskStartedBeforeTermination() {
        G5HistoryFetchResult fetch = G5HistoryFetchResult.complete(
                "wf",
                "run",
                List.of(
                        event(2, EventType.EVENT_TYPE_WORKFLOW_TASK_STARTED),
                        event(9, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED)),
                1);
        G5HistoryValidationResult v = G5HistoryGate.validateFetch(fetch);
        assertThat(v.workflowTaskStartedBeforeTermination()).isTrue();
        assertThat(v.passesAcceptanceGate()).isFalse();
    }

    @Test
    void validateFetch_workflowTaskStartedAfterTermination_ok() {
        G5HistoryFetchResult fetch = G5HistoryFetchResult.complete(
                "wf",
                "run",
                List.of(
                        event(5, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED),
                        event(6, EventType.EVENT_TYPE_WORKFLOW_TASK_STARTED)),
                1);
        assertThat(G5HistoryGate.validateFetch(fetch).passesAcceptanceGate()).isTrue();
    }

    private static HistoryEvent event(long id, EventType type) {
        return HistoryEvent.newBuilder().setEventId(id).setEventType(type).build();
    }

    private static GetWorkflowExecutionHistoryResponse response(
            List<HistoryEvent> events, ByteString next) {
        return GetWorkflowExecutionHistoryResponse.newBuilder()
                .setHistory(History.newBuilder().addAllEvents(events).build())
                .setNextPageToken(next == null ? ByteString.EMPTY : next)
                .build();
    }
}
