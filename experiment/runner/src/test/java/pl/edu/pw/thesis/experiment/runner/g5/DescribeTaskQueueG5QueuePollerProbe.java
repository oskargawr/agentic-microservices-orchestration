package pl.edu.pw.thesis.experiment.runner.g5;

import io.temporal.api.enums.v1.TaskQueueType;
import io.temporal.api.taskqueue.v1.PollerInfo;
import io.temporal.api.taskqueue.v1.TaskQueue;
import io.temporal.api.workflowservice.v1.DescribeTaskQueueRequest;
import io.temporal.api.workflowservice.v1.DescribeTaskQueueResponse;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fail-closed G5-3 poller probe via Temporal {@code DescribeTaskQueue} (workflow + activity).
 *
 * <p><b>Guarantee:</b> at probe time, the Temporal frontend reports zero pollers on the named task
 * queue for {@link TaskQueueType#TASK_QUEUE_TYPE_WORKFLOW} and {@link
 * TaskQueueType#TASK_QUEUE_TYPE_ACTIVITY}. API errors / unreachable server ⇒ treated as pollers
 * present (fail closed).
 *
 * <p><b>Limitations (not independent proofs):</b> does not use {@code THESIS_G5_DEDICATED_ENV} or
 * {@code THESIS_G5_POLLER_DETECTED}; does not prove no poller will appear mid-trial; stale poller
 * entries may linger briefly after worker shutdown; history gate remains a separate post-trial
 * check.
 */
public final class DescribeTaskQueueG5QueuePollerProbe implements G5QueuePollerProbe {

    private final WorkflowServiceStubs stubs;
    private final String namespace;
    private ProbeSnapshot lastSnapshot;

    public DescribeTaskQueueG5QueuePollerProbe(WorkflowServiceStubs stubs, String namespace) {
        this.stubs = Objects.requireNonNull(stubs, "stubs");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
    }

    public ProbeSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    @Override
    public boolean anyPollerDetected(String taskQueue) {
        lastSnapshot = probe(taskQueue);
        return lastSnapshot.pollerDetected() || !lastSnapshot.probeSucceeded();
    }

    public ProbeSnapshot probe(String taskQueue) {
        Objects.requireNonNull(taskQueue, "taskQueue");
        List<String> identities = new ArrayList<>();
        StringBuilder diag = new StringBuilder();
        diag.append("probe=DescribeTaskQueue; namespace=")
                .append(namespace)
                .append("; taskQueue=")
                .append(taskQueue);

        try {
            for (TaskQueueType type :
                    List.of(TaskQueueType.TASK_QUEUE_TYPE_WORKFLOW, TaskQueueType.TASK_QUEUE_TYPE_ACTIVITY)) {
                DescribeTaskQueueResponse response = stubs.blockingStub()
                        .describeTaskQueue(DescribeTaskQueueRequest.newBuilder()
                                .setNamespace(namespace)
                                .setTaskQueue(TaskQueue.newBuilder().setName(taskQueue).build())
                                .setTaskQueueType(type)
                                .setReportPollers(true)
                                .build());
                List<PollerInfo> pollers = response.getPollersList();
                diag.append("; ")
                        .append(type.name())
                        .append(".pollers=")
                        .append(pollers.size());
                for (PollerInfo p : pollers) {
                    String id = p.getIdentity();
                    identities.add(type.name() + ":" + (id == null || id.isBlank() ? "<blank>" : id));
                }
            }
            boolean detected = !identities.isEmpty();
            diag.append("; identities=")
                    .append(identities.isEmpty() ? "[]" : identities.stream().collect(Collectors.joining(",")));
            return new ProbeSnapshot(true, detected, List.copyOf(identities), diag.toString(), null);
        } catch (RuntimeException e) {
            String fail = "DescribeTaskQueue failed: " + e.getMessage();
            diag.append("; error=").append(e.getMessage());
            // Fail closed: cannot establish exclusivity
            return new ProbeSnapshot(false, true, List.of(), diag.toString(), fail);
        }
    }

    /**
     * @param probeSucceeded false when the Temporal API could not be queried successfully
     * @param pollerDetected true when at least one poller identity was reported, or when the probe
     *     failed closed
     */
    public record ProbeSnapshot(
            boolean probeSucceeded,
            boolean pollerDetected,
            List<String> pollerIdentities,
            String diagnostics,
            String failureReason) {}
}
