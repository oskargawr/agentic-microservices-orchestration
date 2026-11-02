package pl.edu.pw.thesis.orchestration.varianta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.common.IdempotencyKeys;

/**
 * T-202-6 — lightweight stability of Idempotency-Key values used by mutating Variant A activities.
 *
 * <p>HTTP header wiring lives in {@link HttpOrderFulfillmentActivities}; this test locks the
 * stable key patterns those calls pass (same key across Temporal retries → service replay).
 */
class OrderFulfillmentIdempotencyKeyStabilityTest {

    @Test
    void t202_6_mutatingActivityKeysAreStableForSameResourceIds() {
        UUID orderId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID reservationId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID paymentId = UUID.fromString("99999999-8888-7777-6666-555555555555");
        UUID shipmentId = UUID.fromString("abcdef01-2345-6789-abcd-ef0123456789");

        assertThat(IdempotencyKeys.reserve(orderId)).isEqualTo(orderId + ":reserve");
        assertThat(IdempotencyKeys.charge(orderId)).isEqualTo(orderId + ":charge");
        assertThat(IdempotencyKeys.arrange(orderId)).isEqualTo(orderId + ":arrange");
        assertThat(IdempotencyKeys.consume(reservationId)).isEqualTo(reservationId + ":consume");
        assertThat(IdempotencyKeys.release(reservationId)).isEqualTo(reservationId + ":release");
        assertThat(IdempotencyKeys.refund(paymentId)).isEqualTo(paymentId + ":refund");
        assertThat(IdempotencyKeys.cancelShipment(shipmentId)).isEqualTo(shipmentId + ":cancel");

        // Same inputs → same keys (retry safety).
        assertThat(IdempotencyKeys.charge(orderId)).isEqualTo(IdempotencyKeys.charge(orderId));
        assertThat(IdempotencyKeys.refund(paymentId)).isEqualTo(IdempotencyKeys.refund(paymentId));
    }
}
