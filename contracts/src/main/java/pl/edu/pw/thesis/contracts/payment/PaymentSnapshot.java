package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;

public record PaymentSnapshot(
        UUID paymentId,
        UUID orderId,
        Money amount,
        PaymentState state
) {
    public PaymentSnapshot {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(state, "state");
    }
}
