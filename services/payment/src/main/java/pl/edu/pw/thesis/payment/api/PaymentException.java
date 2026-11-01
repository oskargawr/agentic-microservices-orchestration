package pl.edu.pw.thesis.payment.api;

import org.springframework.http.HttpStatus;
import pl.edu.pw.thesis.contracts.common.ErrorCode;

public final class PaymentException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final String idempotencyKey;

    public PaymentException(ErrorCode code, HttpStatus status, String message) {
        this(code, status, message, null);
    }

    public PaymentException(ErrorCode code, HttpStatus status, String message, String idempotencyKey) {
        super(message);
        this.code = code;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
