package pl.edu.pw.thesis.orchestration.console.service;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ApplicationFailure;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.inventory.StockSnapshot;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.orchestration.console.config.ConsoleServiceProperties;
import pl.edu.pw.thesis.orchestration.console.config.ConsoleTemporalProperties;
import pl.edu.pw.thesis.orchestration.console.dto.BusinessConsistencyCheck;
import pl.edu.pw.thesis.orchestration.console.dto.BusinessStateResponse;
import pl.edu.pw.thesis.orchestration.console.dto.RunStatusResponse;
import pl.edu.pw.thesis.orchestration.console.dto.StartRunRequest;
import pl.edu.pw.thesis.orchestration.console.dto.StartRunResponse;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

@Service
public class ConsoleRunService {

    private final WorkflowClient workflowClient;
    private final WorkflowServiceStubs stubs;
    private final ConsoleTemporalProperties temporalProperties;
    private final RestClient inventory;
    private final RestClient payment;
    private final RestClient shipping;

    public ConsoleRunService(
            WorkflowClient workflowClient,
            WorkflowServiceStubs stubs,
            ConsoleTemporalProperties temporalProperties,
            ConsoleServiceProperties services) {
        this.workflowClient = workflowClient;
        this.stubs = stubs;
        this.temporalProperties = temporalProperties;
        this.inventory = RestClient.builder().baseUrl(services.getInventoryBaseUrl()).build();
        this.payment = RestClient.builder().baseUrl(services.getPaymentBaseUrl()).build();
        this.shipping = RestClient.builder().baseUrl(services.getShippingBaseUrl()).build();
    }

    public StartRunResponse start(StartRunRequest request) {
        String workflowId = "order-" + request.orderId();
        OrderFulfillmentInput input = new OrderFulfillmentInput(
                request.orderId(),
                request.sku().trim(),
                request.qty(),
                new Money(request.amountMinor(), request.currency().trim()));

        try {
            OrderFulfillmentWorkflow stub = workflowClient.newWorkflowStub(
                    OrderFulfillmentWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(OrderFulfillmentWorkflow.TASK_QUEUE)
                            .setWorkflowId(workflowId)
                            .build());
            WorkflowExecution execution = WorkflowClient.start(stub::fulfill, input);
            return new StartRunResponse(
                    execution.getWorkflowId(),
                    execution.getRunId(),
                    temporalProperties.getNamespace(),
                    OrderFulfillmentWorkflow.TASK_QUEUE,
                    temporalUiUrl(execution.getWorkflowId(), execution.getRunId()));
        } catch (WorkflowExecutionAlreadyStarted e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Workflow already started for workflowId=" + workflowId
                            + ". Use a fresh orderId or inspect the existing run.",
                    e);
        }
    }

    public RunStatusResponse status(String workflowId) {
        WorkflowExecution execution = resolveExecution(workflowId);
        var describe = stubs.blockingStub()
                .describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder()
                        .setNamespace(temporalProperties.getNamespace())
                        .setExecution(execution)
                        .build());

        WorkflowExecutionStatus status = describe.getWorkflowExecutionInfo().getStatus();
        String temporalStatus = mapStatus(status);
        List<HistoryEvent> events = loadHistory(execution);
        var steps = WorkflowHistoryMapper.mapSteps(events);

        OrderFulfillmentResult result = null;
        String failureMessage = null;
        if (status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED) {
            try {
                result = getWorkflowResult(execution.getWorkflowId());
            } catch (Exception e) {
                failureMessage = "Completed but failed to decode result: " + e.getMessage();
            }
        } else if (isUnsuccessful(status)) {
            failureMessage = extractFailure(execution, events);
        }

        return new RunStatusResponse(
                execution.getWorkflowId(),
                execution.getRunId(),
                temporalProperties.getNamespace(),
                temporalStatus,
                temporalUiUrl(execution.getWorkflowId(), execution.getRunId()),
                steps,
                result,
                failureMessage,
                "Temporal completion alone is not business success; use /business for service snapshots.");
    }

    public BusinessStateResponse business(String workflowId) {
        RunStatusResponse status = status(workflowId);
        if (status.workflowResult() == null) {
            return new BusinessStateResponse(
                    false,
                    "Business snapshots not available yet (workflow result / entity IDs missing). "
                            + "Temporal status=" + status.temporalStatus(),
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        OrderFulfillmentResult wr = status.workflowResult();
        ReservationSnapshot reservation = inventory.get()
                .uri("/api/v1/reservations/{id}", wr.reservationId())
                .retrieve()
                .body(ReservationSnapshot.class);
        PaymentSnapshot paymentSnap = payment.get()
                .uri("/api/v1/payments/{id}", wr.paymentId())
                .retrieve()
                .body(PaymentSnapshot.class);
        ShipmentSnapshot shipment = shipping.get()
                .uri("/api/v1/shipments/{id}", wr.shipmentId())
                .retrieve()
                .body(ShipmentSnapshot.class);
        StockSnapshot stock = null;
        if (reservation != null) {
            stock = inventory.get()
                    .uri("/api/v1/stock/{sku}", reservation.sku())
                    .retrieve()
                    .body(StockSnapshot.class);
        }

        boolean ok = reservation != null
                && paymentSnap != null
                && shipment != null
                && reservation.state() == ReservationState.CONSUMED
                && paymentSnap.state() == PaymentState.CAPTURED
                && shipment.state() == ShipmentState.ARRANGED;

        String summary = ok
                ? "Snapshots match CONFIRMED_SUCCESS shape (CONSUMED/CAPTURED/ARRANGED). "
                        + "This is a console consistency check, not the formal experiment oracle."
                : "Snapshots do not match CONFIRMED_SUCCESS shape. reservation="
                        + (reservation == null ? "null" : reservation.state())
                        + " payment=" + (paymentSnap == null ? "null" : paymentSnap.state())
                        + " shipment=" + (shipment == null ? "null" : shipment.state());

        return new BusinessStateResponse(
                true,
                null,
                reservation,
                paymentSnap,
                shipment,
                stock,
                new BusinessConsistencyCheck(ok, summary));
    }

    private OrderFulfillmentResult getWorkflowResult(String workflowId) {
        OrderFulfillmentWorkflow typed =
                workflowClient.newWorkflowStub(OrderFulfillmentWorkflow.class, workflowId);
        return WorkflowStub.fromTyped(typed).getResult(OrderFulfillmentResult.class);
    }

    private WorkflowExecution resolveExecution(String workflowId) {
        try {
            var described = stubs.blockingStub()
                    .describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder()
                            .setNamespace(temporalProperties.getNamespace())
                            .setExecution(WorkflowExecution.newBuilder()
                                    .setWorkflowId(workflowId)
                                    .build())
                            .build());
            return described.getWorkflowExecutionInfo().getExecution();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId, e);
        }
    }

    private List<HistoryEvent> loadHistory(WorkflowExecution execution) {
        var response = stubs.blockingStub()
                .getWorkflowExecutionHistory(GetWorkflowExecutionHistoryRequest.newBuilder()
                        .setNamespace(temporalProperties.getNamespace())
                        .setExecution(execution)
                        .build());
        return response.getHistory().getEventsList();
    }

    private String extractFailure(WorkflowExecution execution, List<HistoryEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            HistoryEvent event = events.get(i);
            if (event.getEventType() == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_FAILED) {
                var fail = event.getWorkflowExecutionFailedEventAttributes();
                if (fail.hasFailure()) {
                    String msg = fail.getFailure().getMessage();
                    if (fail.getFailure().hasApplicationFailureInfo()) {
                        msg = msg + " [" + fail.getFailure().getApplicationFailureInfo().getType() + "]";
                    }
                    return msg;
                }
            }
            if (event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_FAILED) {
                var fail = event.getActivityTaskFailedEventAttributes();
                if (fail.hasFailure()) {
                    return "Activity failed: " + fail.getFailure().getMessage();
                }
            }
        }
        try {
            getWorkflowResult(execution.getWorkflowId());
        } catch (WorkflowFailedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApplicationFailure af) {
                return af.getType() + ": " + af.getOriginalMessage();
            }
            return e.getMessage();
        } catch (Exception e) {
            return e.getMessage();
        }
        return "Workflow ended unsuccessfully (see Temporal UI for details).";
    }

    private String temporalUiUrl(String workflowId, String runId) {
        String base = temporalProperties.getUiBaseUrl().replaceAll("/$", "");
        return base + "/namespaces/" + temporalProperties.getNamespace()
                + "/workflows/" + workflowId + "/" + runId;
    }

    private static boolean isUnsuccessful(WorkflowExecutionStatus status) {
        return status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED
                || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT
                || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TERMINATED
                || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CANCELED;
    }

    private static String mapStatus(WorkflowExecutionStatus status) {
        return switch (status) {
            case WORKFLOW_EXECUTION_STATUS_RUNNING -> "RUNNING";
            case WORKFLOW_EXECUTION_STATUS_COMPLETED -> "COMPLETED";
            case WORKFLOW_EXECUTION_STATUS_FAILED -> "FAILED";
            case WORKFLOW_EXECUTION_STATUS_CANCELED -> "CANCELED";
            case WORKFLOW_EXECUTION_STATUS_TERMINATED -> "TERMINATED";
            case WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW -> "CONTINUED_AS_NEW";
            case WORKFLOW_EXECUTION_STATUS_TIMED_OUT -> "TIMED_OUT";
            default -> status.name().replace("WORKFLOW_EXECUTION_STATUS_", "");
        };
    }
}
