package pl.edu.pw.thesis.contracts.transition;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;

/**
 * Allowed reservation transitions (TH-011 §3.1 / TH-101.1).
 * Terminal for MVP: {@link ReservationState#RELEASED}, {@link ReservationState#CONSUMED}.
 * {@code consume}: single {@code HELD → CONSUMED}; orchestration calls only after confirmed arrange (C-101-1);
 * stock {@code held}/{@code onHand} updated atomically once — replay must not decrement again.
 */
public final class ReservationTransitions {

    private static final Map<ReservationState, Set<ReservationState>> ALLOWED = Map.of(
            ReservationState.HELD, EnumSet.of(ReservationState.RELEASED, ReservationState.CONSUMED)
    );

    private ReservationTransitions() {
    }

    public static boolean canTransition(ReservationState from, ReservationState to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            return true; // idempotent no-op at same state
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static ReservationState requireTransition(ReservationState from, ReservationState to) {
        if (!canTransition(from, to)) {
            throw new IllegalTransitionException(
                    "Illegal reservation transition " + from + " → " + to);
        }
        return to;
    }

    /** Compensation: release hold. Forbidden on CONSUMED. */
    public static ReservationState release(ReservationState current) {
        if (current == ReservationState.RELEASED) {
            return ReservationState.RELEASED;
        }
        return requireTransition(current, ReservationState.RELEASED);
    }

    /** Success path after arrange: consume hold (not a second stock decrement beyond consume semantics). */
    public static ReservationState consume(ReservationState current) {
        if (current == ReservationState.CONSUMED) {
            return ReservationState.CONSUMED;
        }
        return requireTransition(current, ReservationState.CONSUMED);
    }
}
