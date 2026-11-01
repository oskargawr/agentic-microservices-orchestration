package pl.edu.pw.thesis.contracts.common;

public final class IdempotencyConflictException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey, String message) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public ApiError toApiError() {
        return ApiError.of(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, getMessage(), idempotencyKey);
    }
}
