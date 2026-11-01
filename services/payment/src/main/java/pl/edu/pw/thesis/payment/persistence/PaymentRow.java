package pl.edu.pw.thesis.payment.persistence;

import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.payment.PaymentState;

public record PaymentRow(
        UUID paymentId,
        UUID orderId,
        long amountMinor,
        String currency,
        PaymentState state
) {
    public Money money() {
        return new Money(amountMinor, currency);
    }
}
