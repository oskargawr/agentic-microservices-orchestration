package pl.edu.pw.thesis.experiment.runner.g5;

import java.util.Objects;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;

/**
 * Fail-closed G5-3 operational isolation check. Must pass before {@code TrialRunner.run}.
 *
 * <p>Does not start a measured trial when exclusivity cannot be established.
 *
 * <p>{@code dedicatedEnvironmentConfirmed} is an <em>operator acknowledgment</em> that the Temporal
 * deployment is a dedicated lab — it is <b>not</b> independent proof of zero pollers. Poller
 * exclusivity must come from {@link G5QueuePollerProbe} (production path: {@link
 * DescribeTaskQueueG5QueuePollerProbe}). Env-flag probes are unit-test doubles only.
 */
public final class G5IsolationPreflight {

    public static final String TASK_QUEUE = OrderFulfillmentWorkflow.TASK_QUEUE;

    private final G5QueuePollerProbe pollerProbe;
    private final boolean dedicatedEnvironmentConfirmed;

    public G5IsolationPreflight(G5QueuePollerProbe pollerProbe, boolean dedicatedEnvironmentConfirmed) {
        this.pollerProbe = Objects.requireNonNull(pollerProbe, "pollerProbe");
        this.dedicatedEnvironmentConfirmed = dedicatedEnvironmentConfirmed;
    }

    public Result evaluate() {
        StringBuilder diag = new StringBuilder();
        diag.append("taskQueue=").append(TASK_QUEUE);
        diag.append("; dedicatedEnvConfirmed=").append(dedicatedEnvironmentConfirmed);
        diag.append("; dedicatedEnvNote=operator-ack-only-not-poller-proof");

        if (!dedicatedEnvironmentConfirmed) {
            return Result.fail(
                    "dedicated Temporal lab environment not confirmed (operator ack required;"
                            + " not a substitute for DescribeTaskQueue exclusivity)",
                    diag.toString());
        }

        boolean poller;
        try {
            poller = pollerProbe.anyPollerDetected(TASK_QUEUE);
            if (pollerProbe instanceof DescribeTaskQueueG5QueuePollerProbe describeProbe
                    && describeProbe.lastSnapshot() != null) {
                diag.append("; ").append(describeProbe.lastSnapshot().diagnostics());
                if (!describeProbe.lastSnapshot().probeSucceeded()) {
                    return Result.fail(
                            "DescribeTaskQueue exclusivity could not be established: "
                                    + describeProbe.lastSnapshot().failureReason(),
                            diag.toString());
                }
            }
        } catch (RuntimeException e) {
            return Result.fail(
                    "poller probe failed: " + e.getMessage(), diag + "; probeError=" + e.getMessage());
        }
        diag.append("; pollerDetected=").append(poller);
        if (poller) {
            return Result.fail(
                    "task queue exclusivity violated: poller detected on " + TASK_QUEUE,
                    diag.toString());
        }
        return Result.pass(diag.toString());
    }

    public record Result(boolean ok, String preflightResult, String diagnostics, String failureReason) {
        public static Result pass(String diagnostics) {
            return new Result(true, "PASS", diagnostics, null);
        }

        public static Result fail(String reason, String diagnostics) {
            return new Result(false, "FAIL", diagnostics, reason);
        }

        /** Abort before measured trial when false. */
        public void requireOk() {
            if (!ok) {
                throw new IllegalStateException(
                        "G5-3 isolation preflight failed — measured trial must not start: " + failureReason);
            }
        }
    }
}
