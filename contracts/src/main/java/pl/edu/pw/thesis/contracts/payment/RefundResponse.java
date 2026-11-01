package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import java.util.UUID;

public record RefundResponse(
        UUID paymentId,
        PaymentState state
) {
    public RefundResponse {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(state, "state");
    }
}
