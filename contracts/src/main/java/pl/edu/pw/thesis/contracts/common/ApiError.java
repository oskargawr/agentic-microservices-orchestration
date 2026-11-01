package pl.edu.pw.thesis.contracts.common;

import java.util.Objects;

/**
 * Standard error body for mutating APIs (TH-101.1).
 * {@code idempotencyKey} echoes the {@code Idempotency-Key} header when relevant.
 */
public record ApiError(
        ErrorCode code,
        String message,
        String idempotencyKey
) {
    public ApiError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(ErrorCode code, String message, String idempotencyKey) {
        return new ApiError(code, message, idempotencyKey);
    }
}
