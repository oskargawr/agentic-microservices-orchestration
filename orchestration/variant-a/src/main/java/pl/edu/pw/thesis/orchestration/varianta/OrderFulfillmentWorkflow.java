package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

@WorkflowInterface
public interface OrderFulfillmentWorkflow {

    String TASK_QUEUE = "variant-a-order-fulfillment";

    @WorkflowMethod
    OrderFulfillmentResult fulfill(OrderFulfillmentInput input);
}
