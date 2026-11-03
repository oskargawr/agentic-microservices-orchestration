package pl.edu.pw.thesis.experiment.runner.g5;

/** Lab probe: whether any process appears to be polling the Variant A task queue. */
@FunctionalInterface
public interface G5QueuePollerProbe {

    /**
     * @return true if a poller is detected (G5-3 exclusivity violated)
     */
    boolean anyPollerDetected(String taskQueue);
}
