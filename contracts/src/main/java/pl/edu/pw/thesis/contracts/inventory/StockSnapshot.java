package pl.edu.pw.thesis.contracts.inventory;

import java.util.Objects;

/** SKU stock accounting (I1): available = onHand - held. */
public record StockSnapshot(
        String sku,
        int onHand,
        int held
) {
    public StockSnapshot {
        Objects.requireNonNull(sku, "sku");
        if (onHand < 0 || held < 0) {
            throw new IllegalArgumentException("onHand and held must be >= 0");
        }
    }

    public int available() {
        return onHand - held;
    }
}
