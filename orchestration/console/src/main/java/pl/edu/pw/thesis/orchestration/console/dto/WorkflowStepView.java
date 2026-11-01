package pl.edu.pw.thesis.orchestration.console.dto;

public record WorkflowStepView(
        String id,
        String label,
        String activityName,
        String status
) {
}
