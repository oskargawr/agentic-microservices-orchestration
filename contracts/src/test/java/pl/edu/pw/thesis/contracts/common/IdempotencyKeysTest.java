package pl.edu.pw.thesis.contracts.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyKeysTest {

    @Test
    void reserveChargeArrangeUseOrderScopedHeaderValues() {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertEquals(orderId + ":reserve", IdempotencyKeys.reserve(orderId));
        assertEquals(orderId + ":charge", IdempotencyKeys.charge(orderId));
        assertEquals(orderId + ":arrange", IdempotencyKeys.arrange(orderId));
    }

    @Test
    void consumeKeyDistinctFromRelease() {
        UUID reservationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertEquals(reservationId + ":release", IdempotencyKeys.release(reservationId));
        assertTrue(IdempotencyKeys.consume(reservationId).endsWith(":consume"));
    }
}
