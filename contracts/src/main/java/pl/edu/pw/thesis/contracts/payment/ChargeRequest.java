package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;

/** Body for POST /api/v1/payments/charge. Idempotency via {@code Idempotency-Key} header only. */
public record ChargeRequest(
        UUID orderId,
        Money amount
) {
    public ChargeRequest {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        amount.requirePositive();
    }

    public String idempotencyFingerprint() {
        return orderId + "|" + amount.amountMinor() + "|" + amount.currency();
    }
}
