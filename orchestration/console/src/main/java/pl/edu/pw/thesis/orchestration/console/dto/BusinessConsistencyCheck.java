package pl.edu.pw.thesis.orchestration.console.dto;

/**
 * Lightweight local consistency hint for the console UI.
 * Not the formal experiment oracle (TH-401).
 */
public record BusinessConsistencyCheck(
        boolean looksConsistentWithConfirmedSuccess,
        String summary
) {
}
