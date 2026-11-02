package pl.edu.pw.thesis.experiment.oracle.classify;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.experiment.oracle.OraclePattern;
import pl.edu.pw.thesis.experiment.oracle.OracleReasonCode;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.oracle.snapshot.OperationLogEntry;
import pl.edu.pw.thesis.experiment.oracle.snapshot.PaymentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ReservationView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ShipmentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.StockView;

/**
 * Pure Option C classifier (TH-011 / D-011). Snapshot-only — no workflow/LLM inputs.
 */
public final class OutcomeClassifier {

    public ClassificationDecision classify(BusinessSnapshot snapshot) {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(operationLogWarnings(snapshot));

        List<String> violated = new ArrayList<>();
        checkInvariants(snapshot, violated, warnings);
        if (!violated.isEmpty()) {
            return new ClassificationDecision(
                    BusinessOutcome.INVALID,
                    OraclePattern.NONE,
                    reasonForInvalid(violated),
                    violated,
                    warnings);
        }

        // TH-011 §2.1 requires stock in the success snapshot. Missing stock is insufficient
        // evidence (AMBIGUOUS), not a proven I1 numeric violation (INVALID).
        if (isSuccessEntityPackage(snapshot) && snapshot.stock().isEmpty()) {
            return new ClassificationDecision(
                    BusinessOutcome.AMBIGUOUS,
                    OraclePattern.NONE,
                    OracleReasonCode.MISSING_STOCK_EVIDENCE,
                    List.of(),
                    warnings);
        }

        if (isConfirmedSuccess(snapshot)) {
            // Terminal snapshot can check I1 consistency; absolute onHand Δ vs pre-run baseline
            // is not available here — do not claim §2.1 "onHand reduced by qty" is proven.
            warnings.add("STOCK_ONHAND_DECREMENT_NOT_VERIFIED_WITHOUT_BASELINE");
            return new ClassificationDecision(
                    BusinessOutcome.CONFIRMED_SUCCESS,
                    OraclePattern.SUCCESS,
                    OracleReasonCode.NONE,
                    List.of(),
                    warnings);
        }

        // D-012 / protocol-v0: unresolved payment is AMBIGUOUS, never clean F0–F2 failure.
        Optional<PaymentView> payment = snapshot.payment();
        if (payment.isPresent()) {
            PaymentState ps = payment.get().state();
            if (ps == PaymentState.UNKNOWN) {
                return new ClassificationDecision(
                        BusinessOutcome.AMBIGUOUS,
                        OraclePattern.NONE,
                        OracleReasonCode.UNKNOWN_PAYMENT,
                        List.of(),
                        warnings);
            }
            if (ps == PaymentState.PENDING) {
                return new ClassificationDecision(
                        BusinessOutcome.AMBIGUOUS,
                        OraclePattern.NONE,
                        OracleReasonCode.UNRESOLVED_PENDING_PAYMENT,
                        List.of(),
                        warnings);
            }
        }

        if (isF2(snapshot)) {
            return new ClassificationDecision(
                    BusinessOutcome.CONFIRMED_FAILURE,
                    OraclePattern.F2,
                    OracleReasonCode.NONE,
                    List.of(),
                    warnings);
        }
        if (isF1(snapshot)) {
            return new ClassificationDecision(
                    BusinessOutcome.CONFIRMED_FAILURE,
                    OraclePattern.F1,
                    OracleReasonCode.NONE,
                    List.of(),
                    warnings);
        }
        if (isF0(snapshot)) {
            return new ClassificationDecision(
                    BusinessOutcome.CONFIRMED_FAILURE,
                    OraclePattern.F0,
                    OracleReasonCode.NONE,
                    List.of(),
                    warnings);
        }

        if (isPartialSuccessPackage(snapshot)) {
            return new ClassificationDecision(
                    BusinessOutcome.AMBIGUOUS,
                    OraclePattern.NONE,
                    OracleReasonCode.PARTIAL_SUCCESS_PACKAGE,
                    List.of(),
                    warnings);
        }

        if (hasDanglingCompensation(snapshot)) {
            return new ClassificationDecision(
                    BusinessOutcome.AMBIGUOUS,
                    OraclePattern.NONE,
                    OracleReasonCode.COMPENSATION_INCOMPLETE,
                    List.of(),
                    warnings);
        }

        return new ClassificationDecision(
                BusinessOutcome.AMBIGUOUS,
                OraclePattern.NONE,
                OracleReasonCode.OTHER_PARTIAL,
                List.of(),
                warnings);
    }

    private static OracleReasonCode reasonForInvalid(List<String> violated) {
        if (violated.contains("I2") || violated.stream().anyMatch(v -> v.contains("MULTIPLE"))) {
            return OracleReasonCode.MULTIPLE_RESERVATIONS;
        }
        return OracleReasonCode.NONE;
    }

    private static void checkInvariants(
            BusinessSnapshot snapshot, List<String> violated, List<String> warnings) {
        // MVP: more than one reservation row for an order is not allowed (one reserve attempt).
        if (snapshot.reservations().size() > 1) {
            violated.add("I2/MULTIPLE_RESERVATIONS");
        }

        long held = snapshot.reservations().stream()
                .filter(r -> r.state() == ReservationState.HELD)
                .count();
        if (held > 1) {
            violated.add("I2");
        }

        Optional<StockView> stock = snapshot.stock();
        if (stock.isPresent()) {
            StockView s = stock.get();
            if (s.onHand() < 0 || s.held() < 0 || s.onHand() < s.held()) {
                violated.add("I1");
            }
            if (s.held() != s.heldSumFromHeldReservations()) {
                violated.add("I1");
            }
        } else {
            warnings.add("STOCK_ROW_MISSING:" + snapshot.sku());
        }

        Optional<ShipmentView> shipment = snapshot.shipment();
        Optional<PaymentView> payment = snapshot.payment();
        if (shipment.isPresent() && shipment.get().state() == ShipmentState.ARRANGED) {
            if (payment.isEmpty() || payment.get().state() != PaymentState.CAPTURED) {
                violated.add("I3");
            }
        }
        if (shipment.isPresent()
                && shipment.get().state() == ShipmentState.ARRANGED
                && payment.isPresent()
                && payment.get().state() == PaymentState.REFUNDED) {
            violated.add("I3");
        }

        if (payment.isPresent() && payment.get().state() == PaymentState.CAPTURED) {
            Optional<ReservationView> res = snapshot.singleReservation();
            if (snapshot.reservations().size() != 1) {
                // multiple already flagged; also CAPTURED with zero reservations
                if (snapshot.reservations().isEmpty()) {
                    violated.add("I4");
                }
            } else {
                ReservationState rs = res.orElseThrow().state();
                if (rs != ReservationState.HELD && rs != ReservationState.CONSUMED) {
                    // CAPTURED + RELEASED without REFUNDED
                    violated.add("I4");
                }
            }
        }

        // I7: see docs/I7_ENFORCEMENT.md — Oracle does not re-prove idempotent replays from logs.
        // Observable terminal duplicates: multiple reservation rows / multiple HELD (above).
    }

    /** Entity package for success without stock (TH-011 §2.1 still requires stock separately). */
    private static boolean isSuccessEntityPackage(BusinessSnapshot snapshot) {
        Optional<ReservationView> res = snapshot.singleReservation();
        Optional<PaymentView> pay = snapshot.payment();
        Optional<ShipmentView> ship = snapshot.shipment();
        return res.isPresent()
                && res.get().state() == ReservationState.CONSUMED
                && pay.isPresent()
                && pay.get().state() == PaymentState.CAPTURED
                && ship.isPresent()
                && ship.get().state() == ShipmentState.ARRANGED;
    }

    private static boolean isConfirmedSuccess(BusinessSnapshot snapshot) {
        if (!isSuccessEntityPackage(snapshot)) {
            return false;
        }
        Optional<StockView> stock = snapshot.stock();
        if (stock.isEmpty()) {
            return false;
        }
        StockView s = stock.get();
        // I1 must hold; §2.1 "held no longer includes this order’s qty" for CONSUMED
        // is covered when held == sum(HELD) and this reservation is not HELD.
        return s.onHand() >= s.held()
                && s.held() >= 0
                && s.held() == s.heldSumFromHeldReservations();
    }

    private static boolean isF0(BusinessSnapshot snapshot) {
        if (!snapshot.reservations().isEmpty()) {
            return false;
        }
        if (snapshot.payment().isPresent()
                && snapshot.payment().get().state() == PaymentState.CAPTURED) {
            return false;
        }
        if (snapshot.payment().isPresent()
                && snapshot.payment().get().state() == PaymentState.REFUNDED) {
            return false;
        }
        return shipmentAbsentOrNone(snapshot);
    }

    private static boolean isF1(BusinessSnapshot snapshot) {
        Optional<ReservationView> res = snapshot.singleReservation();
        if (res.isEmpty() || res.get().state() != ReservationState.RELEASED) {
            return false;
        }
        if (!paymentNotCapturedOrAbsentOrFailedOrRefunded(snapshot)) {
            return false;
        }
        if (snapshot.payment().isPresent()
                && snapshot.payment().get().state() == PaymentState.CAPTURED) {
            return false;
        }
        return shipmentAbsentOrCancelled(snapshot) && !hasHeld(snapshot) && !hasArranged(snapshot);
    }

    private static boolean isF2(BusinessSnapshot snapshot) {
        Optional<ReservationView> res = snapshot.singleReservation();
        if (res.isEmpty() || res.get().state() != ReservationState.RELEASED) {
            return false;
        }
        Optional<PaymentView> pay = snapshot.payment();
        if (pay.isEmpty()) {
            return false;
        }
        PaymentState ps = pay.get().state();
        // Had capture then refunded, or charge failed
        if (ps != PaymentState.REFUNDED && ps != PaymentState.FAILED) {
            return false;
        }
        return shipmentAbsentOrCancelled(snapshot) && !hasHeld(snapshot) && !hasArranged(snapshot);
    }

    private static boolean paymentNotCapturedOrAbsentOrFailedOrRefunded(BusinessSnapshot snapshot) {
        if (snapshot.payment().isEmpty()) {
            return true;
        }
        PaymentState ps = snapshot.payment().get().state();
        // UNKNOWN/PENDING handled earlier as AMBIGUOUS
        return ps == PaymentState.FAILED || ps == PaymentState.REFUNDED;
    }

    private static boolean isPartialSuccessPackage(BusinessSnapshot snapshot) {
        Optional<ReservationView> res = snapshot.singleReservation();
        Optional<PaymentView> pay = snapshot.payment();
        Optional<ShipmentView> ship = snapshot.shipment();
        return res.isPresent()
                && res.get().state() == ReservationState.HELD
                && pay.isPresent()
                && pay.get().state() == PaymentState.CAPTURED
                && ship.isPresent()
                && ship.get().state() == ShipmentState.ARRANGED;
    }

    private static boolean hasDanglingCompensation(BusinessSnapshot snapshot) {
        if (hasArranged(snapshot)) {
            return true;
        }
        if (snapshot.payment().isPresent()
                && snapshot.payment().get().state() == PaymentState.CAPTURED) {
            return true;
        }
        return hasHeld(snapshot);
    }

    private static boolean hasHeld(BusinessSnapshot snapshot) {
        return snapshot.reservations().stream().anyMatch(r -> r.state() == ReservationState.HELD);
    }

    private static boolean hasArranged(BusinessSnapshot snapshot) {
        return snapshot.shipment().isPresent()
                && snapshot.shipment().get().state() == ShipmentState.ARRANGED;
    }

    private static boolean shipmentAbsentOrNone(BusinessSnapshot snapshot) {
        return snapshot.shipment().isEmpty()
                || snapshot.shipment().get().state() == ShipmentState.NONE;
    }

    private static boolean shipmentAbsentOrCancelled(BusinessSnapshot snapshot) {
        return snapshot.shipment().isEmpty()
                || snapshot.shipment().get().state() == ShipmentState.CANCELLED
                || snapshot.shipment().get().state() == ShipmentState.NONE;
    }

    private static List<String> operationLogWarnings(BusinessSnapshot snapshot) {
        List<String> warnings = new ArrayList<>();
        Optional<PaymentView> pay = snapshot.payment();
        if (pay.isEmpty()) {
            return warnings;
        }
        PaymentState entityState = pay.get().state();
        for (OperationLogEntry log : snapshot.operationLogs()) {
            if (!"payment".equals(log.schema()) || log.detail() == null) {
                continue;
            }
            String detail = log.detail().toUpperCase(Locale.ROOT);
            if (detail.contains("\"PERSISTEDSTATE\":\"CAPTURED\"")
                    && entityState != PaymentState.CAPTURED
                    && entityState != PaymentState.REFUNDED) {
                warnings.add(OracleReasonCode.OPERATION_LOG_ENTITY_MISMATCH.name()
                        + ":payment.persistedState=CAPTURED entity=" + entityState);
            }
            if (entityState == PaymentState.CAPTURED
                    && detail.contains("\"PERSISTEDSTATE\":\"FAILED\"")) {
                warnings.add(OracleReasonCode.OPERATION_LOG_ENTITY_MISMATCH.name()
                        + ":log.persistedState=FAILED entity=CAPTURED");
            }
        }
        return warnings;
    }
}
