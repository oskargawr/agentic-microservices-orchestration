package pl.edu.pw.thesis.experiment.runner.g5;

/**
 * Deterministic lab probe for unit tests and local dry-runs.
 *
 * <p>Treats {@code THESIS_G5_POLLER_DETECTED=true} as a poller present (fail exclusivity). Default:
 * no poller. Live G5 execution authorization may replace this with a stronger process/UI probe
 * without changing production runner code.
 */
public final class EnvFlagG5QueuePollerProbe implements G5QueuePollerProbe {

    public static final String ENV = "THESIS_G5_POLLER_DETECTED";

    @Override
    public boolean anyPollerDetected(String taskQueue) {
        return G5OptIn.isTruthy(System.getenv(ENV));
    }
}
