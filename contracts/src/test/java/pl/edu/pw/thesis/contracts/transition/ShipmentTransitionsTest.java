package pl.edu.pw.thesis.contracts.transition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

class ShipmentTransitionsTest {

    @Test
    void arrangeFromNone() {
        assertEquals(ShipmentState.ARRANGED,
                ShipmentTransitions.arrange(ShipmentState.NONE));
    }

    @Test
    void arrangeIdempotent() {
        assertEquals(ShipmentState.ARRANGED,
                ShipmentTransitions.arrange(ShipmentState.ARRANGED));
    }

    @Test
    void cancelArranged() {
        assertEquals(ShipmentState.CANCELLED,
                ShipmentTransitions.cancel(ShipmentState.ARRANGED));
    }

    @Test
    void cancelNoneIsNoOp() {
        assertEquals(ShipmentState.NONE,
                ShipmentTransitions.cancel(ShipmentState.NONE));
    }

    @Test
    void cancelIdempotent() {
        assertEquals(ShipmentState.CANCELLED,
                ShipmentTransitions.cancel(ShipmentState.CANCELLED));
    }

    @Test
    void cannotArrangeAfterCancel() {
        assertThrows(IllegalTransitionException.class,
                () -> ShipmentTransitions.arrange(ShipmentState.CANCELLED));
    }
}
