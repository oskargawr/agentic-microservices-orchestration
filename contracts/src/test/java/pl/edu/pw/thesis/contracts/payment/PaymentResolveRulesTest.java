package pl.edu.pw.thesis.contracts.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.transition.IllegalTransitionException;

class PaymentResolveRulesTest {

    @Test
    void confirmedCaptureResolvesUnknownToCaptured() {
        assertEquals(PaymentState.CAPTURED,
                PaymentResolveRules.apply(PaymentState.UNKNOWN, ResolveProbeOutcome.CAPTURE_CONFIRMED));
    }

    @Test
    void authoritativeNonCaptureResolvesToFailed() {
        assertEquals(PaymentState.FAILED,
                PaymentResolveRules.apply(
                        PaymentState.PENDING, ResolveProbeOutcome.CAPTURE_DID_NOT_OCCUR));
    }

    @Test
    void inconclusivePreservesUnknown() {
        assertEquals(PaymentState.UNKNOWN,
                PaymentResolveRules.apply(PaymentState.UNKNOWN, ResolveProbeOutcome.INCONCLUSIVE));
    }

    @Test
    void inconclusivePreservesPending() {
        assertEquals(PaymentState.PENDING,
                PaymentResolveRules.apply(PaymentState.PENDING, ResolveProbeOutcome.INCONCLUSIVE));
    }

    @Test
    void timeoutPathIsNotAutomaticFailure() {
        assertEquals(PaymentState.UNKNOWN,
                PaymentResolveRules.apply(PaymentState.UNKNOWN, ResolveProbeOutcome.INCONCLUSIVE));
    }

    @Test
    void alreadyCapturedIsIdempotentOnResolve() {
        assertEquals(PaymentState.CAPTURED,
                PaymentResolveRules.apply(PaymentState.CAPTURED, ResolveProbeOutcome.CAPTURE_CONFIRMED));
    }

    @Test
    void contradictoryProbeAgainstCapturedIsRejected() {
        assertThrows(IllegalTransitionException.class, () ->
                PaymentResolveRules.assertNoContradiction(
                        PaymentState.CAPTURED, ResolveProbeOutcome.CAPTURE_DID_NOT_OCCUR));
    }
}
