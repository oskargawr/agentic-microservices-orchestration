package pl.edu.pw.thesis.experiment.runner.accept;

import java.util.Objects;
import java.util.Optional;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.experiment.oracle.OracleResult;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.oracle.snapshot.PaymentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ReservationView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ShipmentView;
import pl.edu.pw.thesis.experiment.runner.fault.F3UnresolvedFaultController;

/**
 * Harness-level acceptance for F3-Unresolved (D-AUTH-S3-2). Does <strong>not</strong> modify
 * Oracle classification — only validates expected experimental cell evidence.
 */
public final class F3UnresolvedAcceptanceGate {

    private F3UnresolvedAcceptanceGate() {}

    /**
     * @return empty when the trial is not F3-Unresolved or when acceptance passes; otherwise a
     *     reject reason for harness EXCLUDED
     */
    public static Optional<String> rejectReason(String faultProfileId, OracleResult oracleResult) {
        if (!F3UnresolvedFaultController.PROFILE.equals(faultProfileId)) {
            return Optional.empty();
        }
        Objects.requireNonNull(oracleResult, "oracleResult");
        if (oracleResult.classification() != BusinessOutcome.AMBIGUOUS) {
            return Optional.of(
                    "F3-Unresolved requires Oracle AMBIGUOUS, got "
                            + oracleResult.classification());
        }
        if (!oracleResult.violatedInvariants().isEmpty()) {
            return Optional.of(
                    "F3-Unresolved requires no invariant violations, got "
                            + oracleResult.violatedInvariants());
        }
        BusinessSnapshot snap = oracleResult.snapshot();
        Optional<PaymentView> pay = snap.payment();
        if (pay.isEmpty() || pay.get().state() != PaymentState.CAPTURED) {
            return Optional.of("F3-Unresolved requires DB payment CAPTURED");
        }
        Optional<ReservationView> res = snap.singleReservation();
        if (res.isEmpty() || res.get().state() != ReservationState.HELD) {
            return Optional.of("F3-Unresolved requires single reservation HELD");
        }
        Optional<ShipmentView> ship = snap.shipment();
        if (ship.isPresent() && ship.get().state() == ShipmentState.ARRANGED) {
            return Optional.of("F3-Unresolved forbids ARRANGED shipment");
        }
        return Optional.empty();
    }
}
