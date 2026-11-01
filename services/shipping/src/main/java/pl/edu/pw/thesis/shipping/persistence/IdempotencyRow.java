package pl.edu.pw.thesis.shipping.persistence;

public record IdempotencyRow(
        String idempotencyKey,
        String operation,
        String requestFingerprint,
        int httpStatus,
        String responseBody
) {
}
