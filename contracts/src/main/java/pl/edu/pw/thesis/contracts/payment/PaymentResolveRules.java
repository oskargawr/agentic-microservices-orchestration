package pl.edu.pw.thesis.contracts.payment;

import java.util.Objects;
import pl.edu.pw.thesis.contracts.transition.IllegalTransitionException;

/**
 * Semantics for {@code POST /api/v1/payments/{paymentId}/resolve} (TH-101.1).
 *
 * <ul>
 *   <li>{@link PaymentState#CAPTURED} only on confirmed capture.</li>
 *   <li>{@link PaymentState#FAILED} only on authoritative "capture did not occur".</li>
 *   <li>Inconclusive probe preserves {@link PaymentState#UNKNOWN} or {@link PaymentState#PENDING}.</li>
 *   <li>Timeout / missing info must never become confirmed failure.</li>
 *   <li>Implementations must serialize resolve per {@code paymentId}; replays with the same
 *       confirmed outcome are idempotent; contradictory confirmed outcomes are a service bug.</li>
 * </ul>
 */
public final class PaymentResolveRules {

    private PaymentResolveRules() {
    }

    public static PaymentState apply(PaymentState current, ResolveProbeOutcome probe) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(probe, "probe");

        return switch (current) {
            case CAPTURED, FAILED, REFUNDED -> current; // terminal for resolve; idempotent return
            case PENDING, UNKNOWN -> applyProbe(current, probe);
        };
    }

    private static PaymentState applyProbe(PaymentState current, ResolveProbeOutcome probe) {
        return switch (probe) {
            case CAPTURE_CONFIRMED -> PaymentState.CAPTURED;
            case CAPTURE_DID_NOT_OCCUR -> PaymentState.FAILED;
            case INCONCLUSIVE -> current; // keep PENDING or UNKNOWN
        };
    }

    /**
     * Detects contradictory confirmed outcomes if a second probe disagrees with a terminal state.
     */
    public static void assertNoContradiction(PaymentState stored, ResolveProbeOutcome probe) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(probe, "probe");
        if (probe == ResolveProbeOutcome.INCONCLUSIVE) {
            return;
        }
        PaymentState implied = switch (probe) {
            case CAPTURE_CONFIRMED -> PaymentState.CAPTURED;
            case CAPTURE_DID_NOT_OCCUR -> PaymentState.FAILED;
            case INCONCLUSIVE -> throw new IllegalStateException("unreachable");
        };
        if ((stored == PaymentState.CAPTURED || stored == PaymentState.FAILED)
                && stored != implied) {
            throw new IllegalTransitionException(
                    "Contradictory resolve probe: stored=" + stored + " probe=" + probe);
        }
    }
}
