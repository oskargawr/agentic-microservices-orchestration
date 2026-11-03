package pl.edu.pw.thesis.experiment.runner.g5;

import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Test/lab helper: paginated Temporal history fetch for an exact workflowId/runId.
 *
 * <p>{@code historyComplete} is true only after every page is retrieved and {@code nextPageToken}
 * is empty. A single non-empty page does not imply completeness without an empty token.
 */
public final class G5HistoryFetcher {

    /** Small page size so multi-page behavior is testable against real servers too. */
    public static final int DEFAULT_MAXIMUM_PAGE_SIZE = 10;

    private final G5HistoryPageClient pageClient;
    private final String namespace;
    private final int maximumPageSize;

    public G5HistoryFetcher(WorkflowServiceStubs stubs, String namespace) {
        this(stubs, namespace, DEFAULT_MAXIMUM_PAGE_SIZE);
    }

    public G5HistoryFetcher(WorkflowServiceStubs stubs, String namespace, int maximumPageSize) {
        this(
                request -> Objects.requireNonNull(stubs, "stubs")
                        .blockingStub()
                        .getWorkflowExecutionHistory(request),
                namespace,
                maximumPageSize);
    }

    public G5HistoryFetcher(G5HistoryPageClient pageClient, String namespace) {
        this(pageClient, namespace, DEFAULT_MAXIMUM_PAGE_SIZE);
    }

    public G5HistoryFetcher(G5HistoryPageClient pageClient, String namespace, int maximumPageSize) {
        this.pageClient = Objects.requireNonNull(pageClient, "pageClient");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        if (maximumPageSize <= 0) {
            throw new IllegalArgumentException("maximumPageSize must be positive");
        }
        this.maximumPageSize = maximumPageSize;
    }

    /**
     * Fetches all history pages for the exact execution. Fail-closed: any API error or truncated
     * retrieval yields {@link G5HistoryFetchResult#complete()}{@code == false}.
     */
    public G5HistoryFetchResult fetchAll(String workflowId, String runId) {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(runId, "runId");
        if (workflowId.isBlank() || runId.isBlank()) {
            return G5HistoryFetchResult.incomplete(
                    workflowId == null ? "" : workflowId,
                    runId == null ? "" : runId,
                    List.of(),
                    0,
                    "workflowId/runId must be non-blank");
        }

        WorkflowExecution execution = WorkflowExecution.newBuilder()
                .setWorkflowId(workflowId)
                .setRunId(runId)
                .build();

        List<HistoryEvent> events = new ArrayList<>();
        ByteString nextToken = ByteString.EMPTY;
        int pageCount = 0;

        try {
            do {
                GetWorkflowExecutionHistoryRequest.Builder req = GetWorkflowExecutionHistoryRequest.newBuilder()
                        .setNamespace(namespace)
                        .setExecution(execution)
                        .setMaximumPageSize(maximumPageSize);
                if (!nextToken.isEmpty()) {
                    req.setNextPageToken(nextToken);
                }

                GetWorkflowExecutionHistoryResponse response = pageClient.getPage(req.build());
                pageCount++;
                if (response.hasHistory()) {
                    events.addAll(response.getHistory().getEventsList());
                }
                nextToken = response.getNextPageToken();
                if (nextToken == null) {
                    nextToken = ByteString.EMPTY;
                }
            } while (!nextToken.isEmpty());

            return G5HistoryFetchResult.complete(workflowId, runId, events, pageCount);
        } catch (RuntimeException e) {
            return G5HistoryFetchResult.incomplete(
                    workflowId,
                    runId,
                    events,
                    pageCount,
                    "GetWorkflowExecutionHistory failed after page " + pageCount + ": " + e.getMessage());
        }
    }

    /** @deprecated use {@link #fetchAll(String, String)} — single-page fetch cannot prove completeness. */
    @Deprecated
    public List<HistoryEvent> fetch(String workflowId, String runId) {
        G5HistoryFetchResult result = fetchAll(workflowId, runId);
        if (!result.complete()) {
            throw new IllegalStateException("history fetch incomplete: " + result.failureReason());
        }
        return result.events();
    }

    /** Writes a compact diagnostic export (eventId + type name per line). */
    public static Path writeDiagnosticExport(Path target, List<HistoryEvent> events) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(events, "events");
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        StringBuilder sb = new StringBuilder();
        for (HistoryEvent e : events) {
            sb.append(e.getEventId())
                    .append('\t')
                    .append(e.getEventType().name())
                    .append('\n');
        }
        Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
        return target;
    }
}
