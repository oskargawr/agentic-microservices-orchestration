package pl.edu.pw.thesis.contracts.common;

import java.util.Objects;
import java.util.UUID;

/**
 * Mandatory idempotency key helpers (TH-011 §5 / I7).
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
        return requireOrder(orderId).toString();
    }

    public static String release(UUID reservationId) {
        return requireId(reservationId, "reservationId").toString();
    }

    public static String refund(UUID paymentId) {
        return requireId(paymentId, "paymentId").toString();
    }

    public static String cancelShipment(UUID shipmentId) {
        return requireId(shipmentId, "shipmentId").toString();
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
