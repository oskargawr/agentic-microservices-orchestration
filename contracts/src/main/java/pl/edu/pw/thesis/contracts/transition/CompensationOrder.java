package pl.edu.pw.thesis.contracts.transition;

/**
 * Business-level compensation order (TH-011 §6) — reverse of completion.
 * <ol>
 *   <li>Cancel shipment if ARRANGED</li>
 *   <li>Refund if CAPTURED (never refund UNKNOWN/PENDING)</li>
 *   <li>Release inventory if HELD (never release CONSUMED)</li>
 * </ol>
 */
public enum CompensationOrder {
    CANCEL_SHIPMENT,
    REFUND_PAYMENT,
    RELEASE_INVENTORY
}
