package pl.edu.pw.thesis.contracts.transition;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.edu.pw.thesis.contracts.payment.PaymentState;

/**
 * Allowed payment transitions (TH-011 §3.2).
 * Refund is allowed only from {@link PaymentState#CAPTURED}, or from {@link PaymentState#UNKNOWN}
 * when modeling the late-capture-then-refund path (protocol state diagram).
 * Callers must still enforce: do not issue refund API unless capture is confirmed —
 * see {@link #assertRefundAllowed(PaymentState)}.
 */
public final class PaymentTransitions {

    private static final Map<PaymentState, Set<PaymentState>> ALLOWED = Map.of(
            PaymentState.PENDING, EnumSet.of(
                    PaymentState.CAPTURED, PaymentState.FAILED, PaymentState.UNKNOWN),
            PaymentState.UNKNOWN, EnumSet.of(
                    PaymentState.CAPTURED, PaymentState.FAILED, PaymentState.REFUNDED),
            PaymentState.CAPTURED, EnumSet.of(PaymentState.REFUNDED)
    );

    private PaymentTransitions() {
    }

    public static boolean canTransition(PaymentState from, PaymentState to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static PaymentState requireTransition(PaymentState from, PaymentState to) {
        if (!canTransition(from, to)) {
            throw new IllegalTransitionException(
                    "Illegal payment transition " + from + " → " + to);
        }
        return to;
    }

    /**
     * Business rule: do not refund unless capture is confirmed.
     * {@link PaymentState#UNKNOWN} and {@link PaymentState#PENDING} are rejected here
     * even though the protocol diagram allows UNKNOWN→REFUNDED for the late-capture path —
     * that path must first resolve to CAPTURED (or use an explicit resolve+refund sequence in TH-103).
     */
    public static void assertRefundAllowed(PaymentState current) {
        Objects.requireNonNull(current, "current");
        if (current == PaymentState.REFUNDED) {
            return; // idempotent
        }
        if (current != PaymentState.CAPTURED) {
            throw new IllegalTransitionException(
                    "Refund not allowed from " + current + "; capture must be confirmed (CAPTURED)");
        }
    }

    public static PaymentState refund(PaymentState current) {
        assertRefundAllowed(current);
        return requireTransition(current, PaymentState.REFUNDED);
    }

    public static PaymentState markUnknown(PaymentState current) {
        if (current == PaymentState.UNKNOWN) {
            return PaymentState.UNKNOWN;
        }
        return requireTransition(current, PaymentState.UNKNOWN);
    }
}
