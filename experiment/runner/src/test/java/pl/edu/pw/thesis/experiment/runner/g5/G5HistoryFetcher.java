package pl.edu.pw.thesis.experiment.runner.g5;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Test/lab helper: fetch Temporal history for an exact workflowId/runId. */
public final class G5HistoryFetcher {

    private final WorkflowServiceStubs stubs;
    private final String namespace;

    public G5HistoryFetcher(WorkflowServiceStubs stubs, String namespace) {
        this.stubs = Objects.requireNonNull(stubs, "stubs");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
    }

    public List<HistoryEvent> fetch(String workflowId, String runId) {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(runId, "runId");
        var execution = WorkflowExecution.newBuilder()
                .setWorkflowId(workflowId)
                .setRunId(runId)
                .build();
        var response = stubs.blockingStub()
                .getWorkflowExecutionHistory(GetWorkflowExecutionHistoryRequest.newBuilder()
                        .setNamespace(namespace)
                        .setExecution(execution)
                        .build());
        return response.getHistory().getEventsList();
    }

    /** Writes a compact diagnostic export (eventId + type name per line). */
    public static Path writeDiagnosticExport(Path target, List<HistoryEvent> events) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(events, "events");
        Files.createDirectories(target.getParent());
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
