package pl.edu.pw.thesis.experiment.runner.g5;

/**
 * Test/lab view of a Temporal history event. {@code eventId} is the primary ordering key per
 * D-AUTH-G5 / discovery.
 */
public record G5HistoryEventView(long eventId, G5HistoryEventKind kind) {

    public G5HistoryEventView {
        if (eventId < 0) {
            throw new IllegalArgumentException("eventId must be non-negative");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
    }
}
