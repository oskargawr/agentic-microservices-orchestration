package pl.edu.pw.thesis.orchestration.varianta.model;

import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;

/** Stable workflow input (Variant A happy path). */
public record OrderFulfillmentInput(
        UUID orderId,
        String sku,
        int qty,
        Money amount
) {
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
}
