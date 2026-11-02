package pl.edu.pw.thesis.experiment.oracle.snapshot;

import java.util.UUID;
import pl.edu.pw.thesis.contracts.payment.PaymentState;

public record PaymentView(
        UUID paymentId, UUID orderId, long amountMinor, String currency, PaymentState state) {}
