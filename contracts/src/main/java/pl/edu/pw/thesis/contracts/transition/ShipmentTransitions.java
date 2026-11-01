package pl.edu.pw.thesis.contracts.transition;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

/**
 * Allowed shipment transitions (TH-011 §3.3).
 */
public final class ShipmentTransitions {

    private static final Map<ShipmentState, Set<ShipmentState>> ALLOWED = Map.of(
            ShipmentState.NONE, EnumSet.of(ShipmentState.ARRANGED),
            ShipmentState.ARRANGED, EnumSet.of(ShipmentState.CANCELLED)
    );

    private ShipmentTransitions() {
    }

    public static boolean canTransition(ShipmentState from, ShipmentState to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static ShipmentState requireTransition(ShipmentState from, ShipmentState to) {
        if (!canTransition(from, to)) {
            throw new IllegalTransitionException(
                    "Illegal shipment transition " + from + " → " + to);
        }
        return to;
    }

    public static ShipmentState arrange(ShipmentState current) {
        if (current == ShipmentState.ARRANGED) {
            return ShipmentState.ARRANGED;
        }
        return requireTransition(current, ShipmentState.ARRANGED);
    }

    public static ShipmentState cancel(ShipmentState current) {
        if (current == ShipmentState.CANCELLED) {
            return ShipmentState.CANCELLED;
        }
        if (current == ShipmentState.NONE) {
            // cancel with no shipment: stay NONE (idempotent no-op for compensation)
            return ShipmentState.NONE;
        }
        return requireTransition(current, ShipmentState.CANCELLED);
    }
}
