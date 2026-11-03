package pl.edu.pw.thesis.experiment.runner.variant;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

/** Temporal Variant A adapter — start / describe / terminate (R6). */
public final class VariantAAdapter implements VariantAdapter {

    private final WorkflowClient workflowClient;
    private final WorkflowServiceStubs stubs;
    private final String namespace;
    private final RunnerClock clock;
    private final Duration pollInterval;

    public VariantAAdapter(
            WorkflowClient workflowClient,
            WorkflowServiceStubs stubs,
            String namespace,
            RunnerClock clock) {
        this(workflowClient, stubs, namespace, clock, Duration.ofMillis(200));
    }

    public VariantAAdapter(
            WorkflowClient workflowClient,
            WorkflowServiceStubs stubs,
            String namespace,
            RunnerClock clock,
            Duration pollInterval) {
        this.workflowClient = Objects.requireNonNull(workflowClient);
        this.stubs = Objects.requireNonNull(stubs);
        this.namespace = Objects.requireNonNull(namespace);
        this.clock = Objects.requireNonNull(clock);
        this.pollInterval = Objects.requireNonNull(pollInterval);
    }

    @Override
    public ExecutionHandle start(TrialSpec trial, ArmedFaultRecord armed) {
        if (!"A".equals(trial.variant())) {
            throw new IllegalArgumentException("VariantAAdapter only supports variant A");
        }
        String chargeFaultHint = resolveChargeFaultHint(armed);
        String resolveFaultHint = resolveResolveFaultHint(armed);
        String arrangeFaultHint = resolveArrangeFaultHint(armed);
        OrderFulfillmentInput input = new OrderFulfillmentInput(
                trial.orderId(),
                trial.businessInputs().sku(),
                trial.businessInputs().qty(),
                new Money(trial.businessInputs().amountMinor(), trial.businessInputs().currency()),
                chargeFaultHint,
                resolveFaultHint,
                arrangeFaultHint,
                null);

        OrderFulfillmentWorkflow stub = workflowClient.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(OrderFulfillmentWorkflow.TASK_QUEUE)
                        .setWorkflowId(trial.workflowId())
                        .build());
        WorkflowExecution execution = WorkflowClient.start(stub::fulfill, input);
        return new ExecutionHandle(execution.getWorkflowId(), execution.getRunId());
    }

    @Override
    public WorkflowStopInfo describe(ExecutionHandle handle) {
        var executionBuilder = WorkflowExecution.newBuilder().setWorkflowId(handle.workflowId());
        if (handle.runId() != null && !handle.runId().isBlank()) {
            executionBuilder.setRunId(handle.runId());
        }
        var describe = stubs.blockingStub()
                .describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder()
                        .setNamespace(namespace)
                        .setExecution(executionBuilder.build())
                        .build());
        WorkflowExecutionStatus status = describe.getWorkflowExecutionInfo().getStatus();
        String name = mapStatus(status);
        boolean terminal = isTerminal(status);
        String outcome = null;
        if (status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED) {
            outcome = readWorkflowOutcome(handle).orElse(null);
        }
        return new WorkflowStopInfo(name, terminal, clock.now(), outcome);
    }

    @Override
    public WorkflowStopInfo awaitStop(ExecutionHandle handle, Instant deadlineExclusive) {
        WorkflowStopInfo last = describe(handle);
        while (!last.terminal() && clock.now().isBefore(deadlineExclusive)) {
            try {
                clock.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting workflow stop", e);
            }
            last = describe(handle);
        }
        return last;
    }

    @Override
    public void terminate(ExecutionHandle handle, String reason) {
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(
                handle.workflowId(), Optional.ofNullable(handle.runId()), Optional.empty());
        stub.terminate(reason == null ? "T_run_max" : reason);
    }

    @Override
    public Optional<String> readWorkflowOutcome(ExecutionHandle handle) {
        try {
            OrderFulfillmentWorkflow typed =
                    workflowClient.newWorkflowStub(OrderFulfillmentWorkflow.class, handle.workflowId());
            OrderFulfillmentResult result =
                    WorkflowStub.fromTyped(typed).getResult(OrderFulfillmentResult.class);
            if (result == null || result.outcome() == null) {
                return Optional.empty();
            }
            return Optional.of(result.outcome().name());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * F0 / missing / NONE / F2 → null charge hint. F1-P → {@code CHARGE_FAIL}. F3-* →
     * {@code F3_CAPTURE_LOST_RESPONSE}.
     */
    static String resolveChargeFaultHint(ArmedFaultRecord armed) {
        if (armed == null || "NONE".equals(armed.mechanism())) {
            return null;
        }
        if ("F0".equals(armed.faultProfileId()) || "F2".equals(armed.faultProfileId())) {
            return null;
        }
        if ("F1-P".equals(armed.faultProfileId())) {
            if (!"HEADER".equals(armed.mechanism())) {
                throw new IllegalArgumentException("F1-P armed mechanism must be HEADER");
            }
            String code = armed.effectiveConfig() == null ? null : armed.effectiveConfig().get("code");
            if (!"CHARGE_FAIL".equals(code)) {
                throw new IllegalArgumentException(
                        "F1-P effectiveConfig.code must be CHARGE_FAIL, got " + code);
            }
            return "CHARGE_FAIL";
        }
        if ("F3-Recovered".equals(armed.faultProfileId())
                || "F3-Unresolved".equals(armed.faultProfileId())) {
            if (!"HEADER".equals(armed.mechanism())) {
                throw new IllegalArgumentException(armed.faultProfileId() + " mechanism must be HEADER");
            }
            String code = armed.effectiveConfig() == null
                    ? null
                    : armed.effectiveConfig().get("chargeCode");
            if (!"F3_CAPTURE_LOST_RESPONSE".equals(code)) {
                throw new IllegalArgumentException(
                        armed.faultProfileId()
                                + " effectiveConfig.chargeCode must be F3_CAPTURE_LOST_RESPONSE, got "
                                + code);
            }
            return "F3_CAPTURE_LOST_RESPONSE";
        }
        throw new IllegalArgumentException(
                "VariantAAdapter unsupported armed fault profile for charge hint: "
                        + armed.faultProfileId());
    }

    /**
     * F3-Unresolved → {@code RESOLVE_TRANSPORT_OPAQUE}. Other profiles → null (including
     * F3-Recovered, which must expose CAPTURED on resolve/GET).
     */
    static String resolveResolveFaultHint(ArmedFaultRecord armed) {
        if (armed == null || "NONE".equals(armed.mechanism())) {
            return null;
        }
        if (!"F3-Unresolved".equals(armed.faultProfileId())) {
            return null;
        }
        if (!"HEADER".equals(armed.mechanism())) {
            throw new IllegalArgumentException("F3-Unresolved mechanism must be HEADER");
        }
        String code = armed.effectiveConfig() == null
                ? null
                : armed.effectiveConfig().get("resolveCode");
        if (!"RESOLVE_TRANSPORT_OPAQUE".equals(code)) {
            throw new IllegalArgumentException(
                    "F3-Unresolved effectiveConfig.resolveCode must be RESOLVE_TRANSPORT_OPAQUE, got "
                            + code);
        }
        return "RESOLVE_TRANSPORT_OPAQUE";
    }

    /**
     * F0 / missing / NONE / F1-P / F3-* → null arrange hint. F2 HEADER + ARRANGE_REJECTED →
     * {@code ARRANGE_REJECTED}.
     */
    static String resolveArrangeFaultHint(ArmedFaultRecord armed) {
        if (armed == null || "NONE".equals(armed.mechanism())) {
            return null;
        }
        if ("F0".equals(armed.faultProfileId())
                || "F1-P".equals(armed.faultProfileId())
                || "F3-Recovered".equals(armed.faultProfileId())
                || "F3-Unresolved".equals(armed.faultProfileId())) {
            return null;
        }
        if ("F2".equals(armed.faultProfileId())) {
            if (!"HEADER".equals(armed.mechanism())) {
                throw new IllegalArgumentException("F2 armed mechanism must be HEADER");
            }
            String code = armed.effectiveConfig() == null ? null : armed.effectiveConfig().get("code");
            if (!"ARRANGE_REJECTED".equals(code)) {
                throw new IllegalArgumentException(
                        "F2 effectiveConfig.code must be ARRANGE_REJECTED, got " + code);
            }
            return "ARRANGE_REJECTED";
        }
        throw new IllegalArgumentException(
                "VariantAAdapter unsupported armed fault profile for arrange hint: "
                        + armed.faultProfileId());
    }

    private static boolean isTerminal(WorkflowExecutionStatus status) {
        return status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED
                || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED
                || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CANCELED
                || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TERMINATED
                || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT
                || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW;
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
            default -> status.name();
        };
    }
}
