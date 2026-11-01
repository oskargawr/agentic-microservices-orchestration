package pl.edu.pw.thesis.contracts.common;

import java.util.Objects;

/**
 * Fixed-point money for MVP contracts.
 * <p><strong>Assumption (contracts):</strong> amounts are integer minor units (e.g. grosze)
 * with ISO-4217 currency code. Protocol-v0 uses a fixed order amount — exact value chosen in TH-103.
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
}
