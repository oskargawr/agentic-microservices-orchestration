package pl.edu.pw.thesis.contracts.order;

/**
 * Orchestration-view aggregate (TH-011 §3.4). Informational for A/C workflows —
 * <strong>not</strong> an oracle input. Oracle uses entity snapshots only.
 */
public enum OrderProcessState {
    STARTED,
    RESERVED,
    PAID,
    SHIPPED,
    COMPENSATING,
    CONFIRMED_SUCCESS,
    CONFIRMED_FAILURE,
    AMBIGUOUS,
    FAILED_CLEAN
}
