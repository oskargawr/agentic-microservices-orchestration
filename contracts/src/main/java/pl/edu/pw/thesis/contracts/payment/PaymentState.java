package pl.edu.pw.thesis.contracts.payment;

/**
 * Payment lifecycle (TH-011 / protocol-v0).
 * Timeout / lost response → {@link #UNKNOWN}; timeout does <em>not</em> mean failure.
 * Do not refund unless capture is confirmed ({@link #CAPTURED}).
 */
public enum PaymentState {
    PENDING,
    CAPTURED,
    FAILED,
    UNKNOWN,
    REFUNDED
}
