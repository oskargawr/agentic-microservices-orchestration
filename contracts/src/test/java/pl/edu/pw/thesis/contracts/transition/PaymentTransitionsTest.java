package pl.edu.pw.thesis.contracts.transition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.payment.PaymentState;

class PaymentTransitionsTest {

    @Test
    void pendingCanBecomeCapturedFailedOrUnknown() {
        assertEquals(PaymentState.CAPTURED,
                PaymentTransitions.requireTransition(PaymentState.PENDING, PaymentState.CAPTURED));
        assertEquals(PaymentState.FAILED,
                PaymentTransitions.requireTransition(PaymentState.PENDING, PaymentState.FAILED));
        assertEquals(PaymentState.UNKNOWN,
                PaymentTransitions.markUnknown(PaymentState.PENDING));
    }

    @Test
    void unknownIsDistinctFromFailed() {
        assertFalse(PaymentState.UNKNOWN.equals(PaymentState.FAILED));
        assertEquals(PaymentState.UNKNOWN,
                PaymentTransitions.markUnknown(PaymentState.PENDING));
    }

    @Test
    void refundOnlyFromCaptured() {
        assertEquals(PaymentState.REFUNDED,
                PaymentTransitions.refund(PaymentState.CAPTURED));
        assertThrows(IllegalTransitionException.class,
                () -> PaymentTransitions.refund(PaymentState.PENDING));
        assertThrows(IllegalTransitionException.class,
                () -> PaymentTransitions.refund(PaymentState.UNKNOWN));
        assertThrows(IllegalTransitionException.class,
                () -> PaymentTransitions.refund(PaymentState.FAILED));
    }

    @Test
    void refundIdempotentWhenAlreadyRefunded() {
        assertEquals(PaymentState.REFUNDED,
                PaymentTransitions.refund(PaymentState.REFUNDED));
    }

    @Test
    void unknownCanResolveToCapturedOrFailed() {
        assertEquals(PaymentState.CAPTURED,
                PaymentTransitions.requireTransition(PaymentState.UNKNOWN, PaymentState.CAPTURED));
        assertEquals(PaymentState.FAILED,
                PaymentTransitions.requireTransition(PaymentState.UNKNOWN, PaymentState.FAILED));
    }

    @Test
    void cannotCaptureAfterFailed() {
        assertThrows(IllegalTransitionException.class,
                () -> PaymentTransitions.requireTransition(
                        PaymentState.FAILED, PaymentState.CAPTURED));
    }

    @Test
    void timeoutPathDoesNotEqualFailureTransition() {
        // Client timeout → UNKNOWN, not FAILED
        assertEquals(PaymentState.UNKNOWN,
                PaymentTransitions.markUnknown(PaymentState.PENDING));
        assertFalse(PaymentTransitions.canTransition(
                PaymentState.PENDING, PaymentState.REFUNDED));
    }
}
