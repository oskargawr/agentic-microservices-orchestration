package pl.edu.pw.thesis.experiment.runner.accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.experiment.oracle.OraclePattern;
import pl.edu.pw.thesis.experiment.oracle.OracleReasonCode;
import pl.edu.pw.thesis.experiment.oracle.OracleResult;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.oracle.snapshot.PaymentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ReservationView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ShipmentView;

class F3UnresolvedAcceptanceGateTest {

    @Test
    void nonF3Profile_passes() {
        assertThat(F3UnresolvedAcceptanceGate.rejectReason("F0", ambiguousCapturedHeld())).isEmpty();
    }

    @Test
    void f3Unresolved_acceptsAmbiguousCapturedHeld() {
        assertThat(F3UnresolvedAcceptanceGate.rejectReason(
                        "F3-Unresolved", ambiguousCapturedHeld()))
                .isEmpty();
    }

    @Test
    void f3Unresolved_rejectsNonAmbiguous() {
        OracleResult success = new OracleResult(
                BusinessOutcome.CONFIRMED_SUCCESS,
                OraclePattern.SUCCESS,
                OracleReasonCode.NONE,
                List.of(),
                List.of(),
                ambiguousCapturedHeld().snapshot(),
                "wf",
                "t",
                OracleResult.EVALUATOR_VERSION,
                Instant.parse("2026-11-03T00:00:00Z"));
        assertThat(F3UnresolvedAcceptanceGate.rejectReason("F3-Unresolved", success))
                .isPresent()
                .get()
                .asString()
                .contains("AMBIGUOUS");
    }

    @Test
    void f3Unresolved_rejectsArrangedShipment() {
        UUID orderId = UUID.randomUUID();
        BusinessSnapshot snap = new BusinessSnapshot(
                orderId,
                "SKU-MVP-1",
                List.of(new ReservationView(
                        UUID.randomUUID(), orderId, "SKU-MVP-1", 1, ReservationState.HELD)),
                Optional.of(new PaymentView(
                        UUID.randomUUID(), orderId, 100, "PLN", PaymentState.CAPTURED)),
                Optional.of(new ShipmentView(UUID.randomUUID(), orderId, ShipmentState.ARRANGED)),
                Optional.empty(),
                List.of());
        OracleResult result = new OracleResult(
                BusinessOutcome.AMBIGUOUS,
                OraclePattern.NONE,
                OracleReasonCode.COMPENSATION_INCOMPLETE,
                List.of(),
                List.of(),
                snap,
                "wf",
                "t",
                OracleResult.EVALUATOR_VERSION,
                Instant.parse("2026-11-03T00:00:00Z"));
        assertThat(F3UnresolvedAcceptanceGate.rejectReason("F3-Unresolved", result))
                .isPresent()
                .get()
                .asString()
                .contains("ARRANGED");
    }

    private static OracleResult ambiguousCapturedHeld() {
        UUID orderId = UUID.randomUUID();
        BusinessSnapshot snap = new BusinessSnapshot(
                orderId,
                "SKU-MVP-1",
                List.of(new ReservationView(
                        UUID.randomUUID(), orderId, "SKU-MVP-1", 1, ReservationState.HELD)),
                Optional.of(new PaymentView(
                        UUID.randomUUID(), orderId, 100, "PLN", PaymentState.CAPTURED)),
                Optional.empty(),
                Optional.empty(),
                List.of());
        return new OracleResult(
                BusinessOutcome.AMBIGUOUS,
                OraclePattern.NONE,
                OracleReasonCode.COMPENSATION_INCOMPLETE,
                List.of(),
                List.of(),
                snap,
                "wf",
                "t",
                OracleResult.EVALUATOR_VERSION,
                Instant.parse("2026-11-03T00:00:00Z"));
    }
}
