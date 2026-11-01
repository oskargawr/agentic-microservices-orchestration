package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;

public record ChargeRequest(
        UUID orderId,
        Money amount,
        String idempotencyKey
) {
    public ChargeRequest {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
