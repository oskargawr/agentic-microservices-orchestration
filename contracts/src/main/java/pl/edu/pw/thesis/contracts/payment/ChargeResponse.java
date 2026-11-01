package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;

/**
 * {@code state} may be {@link PaymentState#CAPTURED}, {@link PaymentState#FAILED},
 * {@link PaymentState#PENDING}, or {@link PaymentState#UNKNOWN} (client timeout / ambiguity).
 */
public record ChargeResponse(
        UUID paymentId,
        UUID orderId,
        Money amount,
        PaymentState state
) {
    public ChargeResponse {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(state, "state");
    }
}
