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
    ARRANGE_REJECTED,
    /**
     * TH-400 F3-Unresolved: transport opacity on resolve/GET ({@code HTTP 503}).
     * Does not mutate payment DB state.
     */
    SERVICE_UNAVAILABLE
}
