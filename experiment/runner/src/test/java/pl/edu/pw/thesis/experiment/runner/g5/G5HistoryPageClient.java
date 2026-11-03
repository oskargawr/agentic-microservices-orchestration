package pl.edu.pw.thesis.experiment.runner.g5;

import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;

/** Testable seam for paginated {@code GetWorkflowExecutionHistory}. */
@FunctionalInterface
public interface G5HistoryPageClient {

    GetWorkflowExecutionHistoryResponse getPage(GetWorkflowExecutionHistoryRequest request);
}
