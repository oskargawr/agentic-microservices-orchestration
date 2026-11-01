package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import java.util.UUID;

public record RefundRequest(
        UUID paymentId,
        String idempotencyKey
) {
    public RefundRequest {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
