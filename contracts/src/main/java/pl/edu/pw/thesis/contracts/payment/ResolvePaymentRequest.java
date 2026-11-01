package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import java.util.UUID;

/**
 * Probe / resolve for {@link PaymentState#UNKNOWN} or stuck {@link PaymentState#PENDING}.
 * Result may flip to CAPTURED / FAILED (or stay UNKNOWN).
 */
public record ResolvePaymentRequest(
        UUID paymentId
) {
    public ResolvePaymentRequest {
        Objects.requireNonNull(paymentId, "paymentId");
    }
}
