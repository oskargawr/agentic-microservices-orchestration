package pl.edu.pw.thesis.orchestration.varianta.model;

import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;

/**
 * Variant A workflow input.
 *
 * <p>{@code chargeFaultHint} / {@code resolveFaultHint} map to payment {@code X-Thesis-Fault}
 * when the payment service has fault injection enabled — test/experiment control only, not protocol
 * constants. {@code maxPaymentResolveAttempts} bounds UNKNOWN resolution (default
 * {@link #DEFAULT_RESOLVE_ATTEMPTS}).
 */
public record OrderFulfillmentInput(
        UUID orderId,
        String sku,
        int qty,
        Money amount,
        String chargeFaultHint,
        String resolveFaultHint,
        Integer maxPaymentResolveAttempts
) {
    public static final int DEFAULT_RESOLVE_ATTEMPTS = 3;

    public OrderFulfillmentInput(UUID orderId, String sku, int qty, Money amount) {
        this(orderId, sku, qty, amount, null, null, null);
    }

    public OrderFulfillmentInput {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(amount, "amount");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
        amount.requirePositive();
    }

    public int resolveAttemptLimit() {
        if (maxPaymentResolveAttempts == null || maxPaymentResolveAttempts < 1) {
            return DEFAULT_RESOLVE_ATTEMPTS;
        }
        return maxPaymentResolveAttempts;
    }
}
