package pl.edu.pw.thesis.orchestration.console.dto;

import java.util.List;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

public record RunStatusResponse(
        String workflowId,
        String runId,
        String namespace,
        String temporalStatus,
        String temporalUiUrl,
        List<WorkflowStepView> steps,
        OrderFulfillmentResult workflowResult,
        String failureMessage,
        String note
) {
}
