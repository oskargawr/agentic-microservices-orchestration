package pl.edu.pw.thesis.contracts.common;

/**
 * Stable API error codes for A/C shared services (TH-101.1).
 */
public enum ErrorCode {
    INSUFFICIENT_STOCK,
    INVALID_STATE_TRANSITION,
    IDEMPOTENCY_KEY_CONFLICT,
    NOT_FOUND,
    VALIDATION_ERROR,
    REFUND_NOT_ALLOWED,
    /** TH-400 F2: shipping arrange rejected before shipment insert (fault injection). */
    ARRANGE_REJECTED
}
