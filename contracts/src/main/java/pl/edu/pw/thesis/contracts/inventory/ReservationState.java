package pl.edu.pw.thesis.contracts.inventory;

/**
 * Reservation lifecycle (TH-011 / protocol-v0).
 * Success path: {@code HELD → CONSUMED} on successful arrange (no double stock decrement).
 */
public enum ReservationState {
    HELD,
    RELEASED,
    CONSUMED
}
