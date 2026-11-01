package pl.edu.pw.thesis.contracts.common;

import java.util.Objects;

/**
 * Fixed-point money (Owner C-101-2): integer minor units + ISO-4217 currency.
 * Exact charge amount for MVP experiments is chosen in TH-103; representation is frozen here.
 */
public record Money(long amountMinor, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (amountMinor < 0) {
            throw new IllegalArgumentException("amountMinor must be >= 0");
        }
    }

    public static Money plnMinor(long amountMinor) {
        return new Money(amountMinor, "PLN");
    }

    /** Charge amounts must be strictly positive. */
    public Money requirePositive() {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be > 0 for charge");
        }
        return this;
    }
}
