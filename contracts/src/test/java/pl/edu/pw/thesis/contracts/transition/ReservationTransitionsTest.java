package pl.edu.pw.thesis.contracts.transition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;

class ReservationTransitionsTest {

    @Test
    void heldToConsumedIsSuccessPath() {
        assertEquals(ReservationState.CONSUMED,
                ReservationTransitions.consume(ReservationState.HELD));
    }

    @Test
    void heldToReleasedIsCompensation() {
        assertEquals(ReservationState.RELEASED,
                ReservationTransitions.release(ReservationState.HELD));
    }

    @Test
    void consumeIsIdempotent() {
        assertEquals(ReservationState.CONSUMED,
                ReservationTransitions.consume(ReservationState.CONSUMED));
    }

    @Test
    void releaseIsIdempotent() {
        assertEquals(ReservationState.RELEASED,
                ReservationTransitions.release(ReservationState.RELEASED));
    }

    @Test
    void cannotReleaseConsumed() {
        assertThrows(IllegalTransitionException.class,
                () -> ReservationTransitions.release(ReservationState.CONSUMED));
    }

    @Test
    void cannotConsumeReleased() {
        assertThrows(IllegalTransitionException.class,
                () -> ReservationTransitions.consume(ReservationState.RELEASED));
    }

    @Test
    void cannotReturnToHeld() {
        assertFalse(ReservationTransitions.canTransition(
                ReservationState.RELEASED, ReservationState.HELD));
        assertFalse(ReservationTransitions.canTransition(
                ReservationState.CONSUMED, ReservationState.HELD));
        assertThrows(IllegalTransitionException.class,
                () -> ReservationTransitions.requireTransition(
                        ReservationState.CONSUMED, ReservationState.HELD));
    }

    @Test
    void sameStateIsAllowedNoOp() {
        assertTrue(ReservationTransitions.canTransition(
                ReservationState.HELD, ReservationState.HELD));
    }
}
