package pl.edu.pw.thesis.contracts.common;

import java.util.Objects;
import java.util.UUID;

/**
 * Recommended {@code Idempotency-Key} <em>header values</em> (TH-011 §5 / TH-101.1).
 * Keys are not placed in JSON bodies.
 *
 * <p>MVP: one SKU per order; {@code reserve}/{@code charge}/{@code arrange} are scoped by {@code orderId}.
 */
public final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    public static String reserve(UUID orderId) {
        return requireOrder(orderId) + ":reserve";
    }

    public static String charge(UUID orderId) {
        return requireOrder(orderId) + ":charge";
    }

    public static String arrange(UUID orderId) {
        return requireOrder(orderId) + ":arrange";
    }

    public static String release(UUID reservationId) {
        return requireId(reservationId, "reservationId") + ":release";
    }

    public static String refund(UUID paymentId) {
        return requireId(paymentId, "paymentId") + ":refund";
    }

    public static String cancelShipment(UUID shipmentId) {
        return requireId(shipmentId, "shipmentId") + ":cancel";
    }

    public static String consume(UUID reservationId) {
        return requireId(reservationId, "reservationId") + ":consume";
    }

    private static UUID requireOrder(UUID orderId) {
        return requireId(orderId, "orderId");
    }

    private static UUID requireId(UUID id, String name) {
        return Objects.requireNonNull(id, name);
    }
}
