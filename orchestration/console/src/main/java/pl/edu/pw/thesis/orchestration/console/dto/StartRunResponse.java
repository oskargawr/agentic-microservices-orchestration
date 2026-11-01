package pl.edu.pw.thesis.orchestration.console.dto;

public record StartRunResponse(
        String workflowId,
        String runId,
        String namespace,
        String taskQueue,
        String temporalUiUrl
) {
}
