package pl.edu.pw.thesis.experiment.runner.g5;

/**
 * Deterministic <b>unit-test double</b> for {@link G5IsolationPreflight} wiring tests.
 *
 * <p><b>Not</b> isolation evidence for live G5-3. Live preflight must use {@link
 * DescribeTaskQueueG5QueuePollerProbe}. {@code THESIS_G5_POLLER_DETECTED} must never be treated as
 * proof of queue exclusivity.
 */
public final class EnvFlagG5QueuePollerProbe implements G5QueuePollerProbe {

    public static final String ENV = "THESIS_G5_POLLER_DETECTED";

    private final boolean pollerDetected;

    public EnvFlagG5QueuePollerProbe() {
        this(G5OptIn.isTruthy(System.getenv(ENV)));
    }

    public EnvFlagG5QueuePollerProbe(boolean pollerDetected) {
        this.pollerDetected = pollerDetected;
    }

    @Override
    public boolean anyPollerDetected(String taskQueue) {
        return pollerDetected;
    }
}
