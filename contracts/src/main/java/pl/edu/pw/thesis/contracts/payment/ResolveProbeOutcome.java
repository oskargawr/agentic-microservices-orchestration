package pl.edu.pw.thesis.contracts.payment;

/**
 * Authoritative result of a payment resolve probe (TH-101.1).
 * Inconclusive probes must not invent {@link PaymentState#FAILED}.
 */
public enum ResolveProbeOutcome {
    /** Confirmed capture occurred. */
    CAPTURE_CONFIRMED,
    /** Authoritative confirmation that capture did not occur. */
    CAPTURE_DID_NOT_OCCUR,
    /** Probe timed out / missing information — leave UNKNOWN or PENDING. */
    INCONCLUSIVE
}
