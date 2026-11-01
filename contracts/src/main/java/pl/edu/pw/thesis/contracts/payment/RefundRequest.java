package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import java.util.UUID;

/** Logical refund intent. Idempotency via {@code Idempotency-Key} header only. */
public record RefundRequest(
        UUID paymentId
) {
    public RefundRequest {
        Objects.requireNonNull(paymentId, "paymentId");
    }

    public String idempotencyFingerprint() {
        return paymentId.toString();
    }
}
