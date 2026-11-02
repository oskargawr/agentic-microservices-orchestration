package pl.edu.pw.thesis.experiment.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ReservationView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.StockView;
import pl.edu.pw.thesis.experiment.oracle.support.SnapshotFixtures;

class ScenarioClassificationTest {

    private final IndependentOracle oracle = IndependentOracle.classifierOnly();

    @Test
    void s1_fullSuccess() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.CONSUMED, PaymentState.CAPTURED, ShipmentState.ARRANGED),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_SUCCESS);
        assertThat(r.pattern()).isEqualTo(OraclePattern.SUCCESS);
        assertThat(r.warnings())
                .contains("STOCK_ONHAND_DECREMENT_NOT_VERIFIED_WITHOUT_BASELINE");
    }

    @Test
    void missingStock_withSuccessEntities_neverConfirmedSuccess() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId,
                        ReservationState.CONSUMED,
                        PaymentState.CAPTURED,
                        ShipmentState.ARRANGED,
                        List.of(),
                        false),
                null,
                null);
        assertThat(r.classification()).isNotEqualTo(BusinessOutcome.CONFIRMED_SUCCESS);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.pattern()).isEqualTo(OraclePattern.NONE);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.MISSING_STOCK_EVIDENCE);
    }

    @Test
    void s2_f0_beforeResources() {
        UUID orderId = UUID.randomUUID();
        OracleResult r =
                oracle.evaluateSnapshot(SnapshotFixtures.snapshot(orderId, null, null, null), null, null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(r.pattern()).isEqualTo(OraclePattern.F0);
    }

    @Test
    void s3_f2_afterCaptureCompensated() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.RELEASED, PaymentState.REFUNDED, null),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(r.pattern()).isEqualTo(OraclePattern.F2);
    }

    @Test
    void s4_afterArrangeCompensatedBeforeConsume() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId,
                        ReservationState.RELEASED,
                        PaymentState.REFUNDED,
                        ShipmentState.CANCELLED),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(r.pattern()).isEqualTo(OraclePattern.F2);
    }

    @Test
    void s5_postChargeAbortClean() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.RELEASED, PaymentState.REFUNDED, null),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(r.pattern()).isEqualTo(OraclePattern.F2);
    }

    @Test
    void s6_incompleteCapturedHeld() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.HELD, PaymentState.CAPTURED, null),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.pattern()).isEqualTo(OraclePattern.NONE);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.COMPENSATION_INCOMPLETE);
    }

    @Test
    void s6b_capturedReleasedWithoutRefund_invalidI4() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.RELEASED, PaymentState.CAPTURED, null),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.INVALID);
        assertThat(r.violatedInvariants()).contains("I4");
    }

    @Test
    void s7_incompleteArrangedRemains() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.HELD, PaymentState.CAPTURED, ShipmentState.ARRANGED),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.pattern()).isEqualTo(OraclePattern.NONE);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.PARTIAL_SUCCESS_PACKAGE);
    }

    @Test
    void s8_f3_dbCapturedHeld() {
        UUID orderId = UUID.randomUUID();
        var logs = List.of(SnapshotFixtures.paymentLog(
                orderId,
                "{\"persistedState\":\"CAPTURED\",\"observedResponse\":\"UNKNOWN\",\"fault\":\"F3\"}"));
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.HELD, PaymentState.CAPTURED, null, logs),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.COMPENSATION_INCOMPLETE);
        assertThat(r.snapshot().payment().orElseThrow().state()).isEqualTo(PaymentState.CAPTURED);
    }

    @Test
    void s9_paymentUnknown() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.RELEASED, PaymentState.UNKNOWN, null),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.UNKNOWN_PAYMENT);
        assertThat(r.pattern()).isEqualTo(OraclePattern.NONE);
    }

    @Test
    void s10_th208_lostId_dbHasCapturedHeld() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.HELD, PaymentState.CAPTURED, null),
                "wf-without-ids",
                "trial-1");
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.COMPENSATION_INCOMPLETE);
        assertThat(r.workflowId()).isEqualTo("wf-without-ids");
    }

    @Test
    void s11_operationLogVsEntity_warningPreferEntity() {
        UUID orderId = UUID.randomUUID();
        var logs = List.of(SnapshotFixtures.paymentLog(
                orderId, "{\"persistedState\":\"FAILED\",\"observedResponse\":\"FAILED\"}"));
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.HELD, PaymentState.CAPTURED, null, logs),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.warnings()).anyMatch(w -> w.contains("OPERATION_LOG_ENTITY_MISMATCH"));
    }

    @Test
    void s12_workflowClaimsSuccess_dbWrong() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.HELD, PaymentState.CAPTURED, null),
                "wf-claimed-success",
                null);
        assertThat(r.classification()).isNotEqualTo(BusinessOutcome.CONFIRMED_SUCCESS);
    }

    @Test
    void s13_workflowClaimsFailure_dbCleanF2() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.RELEASED, PaymentState.REFUNDED, null),
                "wf-claimed-failure",
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(r.pattern()).isEqualTo(OraclePattern.F2);
    }

    @Test
    void s14_arrangedCapturedHeld_partial() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.HELD, PaymentState.CAPTURED, ShipmentState.ARRANGED),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.pattern()).isEqualTo(OraclePattern.NONE);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.PARTIAL_SUCCESS_PACKAGE);
    }

    @Test
    void f1_releasedNoPayment() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(orderId, ReservationState.RELEASED, null, null), null, null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(r.pattern()).isEqualTo(OraclePattern.F1);
    }

    @Test
    void multipleReservations_invalid() {
        UUID orderId = UUID.randomUUID();
        var r1 = new ReservationView(
                UUID.randomUUID(), orderId, SnapshotFixtures.SKU, 1, ReservationState.HELD);
        var r2 = new ReservationView(
                UUID.randomUUID(), orderId, SnapshotFixtures.SKU, 1, ReservationState.RELEASED);
        BusinessSnapshot snap = new BusinessSnapshot(
                orderId,
                SnapshotFixtures.SKU,
                List.of(r1, r2),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new StockView(SnapshotFixtures.SKU, 100, 1, 1)),
                List.of());
        OracleResult r = oracle.evaluateSnapshot(snap, null, null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.INVALID);
    }
}
