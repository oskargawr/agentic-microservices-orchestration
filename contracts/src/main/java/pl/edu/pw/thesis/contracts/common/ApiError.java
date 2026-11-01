package pl.edu.pw.thesis.contracts.common;

/**
 * Standard error body for mutating APIs (TH-101).
 */
public record ApiError(
        String code,
        String message,
        String idempotencyKey
) {
}
