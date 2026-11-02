package pl.edu.pw.thesis.experiment.runner.model;

import java.util.Objects;

/** Experiment business inputs (D-R8: amountMinor/currency are config, not protocol constants). */
public record BusinessInputs(String sku, int qty, long amountMinor, String currency) {

    public static final String DEFAULT_SKU = "SKU-MVP-1";
    public static final int DEFAULT_QTY = 1;
    public static final long DEFAULT_AMOUNT_MINOR = 2599L;
    public static final String DEFAULT_CURRENCY = "PLN";

    public BusinessInputs {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(currency, "currency");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    public static BusinessInputs protocolPilotDefaults() {
        return new BusinessInputs(DEFAULT_SKU, DEFAULT_QTY, DEFAULT_AMOUNT_MINOR, DEFAULT_CURRENCY);
    }
}
