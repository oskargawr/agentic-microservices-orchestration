package pl.edu.pw.thesis.experiment.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.experiment.oracle.support.SnapshotFixtures;

/**
 * IND-1…6: oracle classification ignores fabricated workflow/LLM claims.
 * No Temporal server and no LLM runtime.
 */
class IndependenceTest {

    private final IndependentOracle oracle = IndependentOracle.classifierOnly();

    @Test
    void ind1_dbSuccess_ignoresWorkflowCompensatedFailureClaim() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.CONSUMED, PaymentState.CAPTURED, ShipmentState.ARRANGED),
                "wf-reported-COMPENSATED_FAILURE",
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_SUCCESS);
    }

    @Test
    void ind2_dbCleanF2_ignoresWorkflowSuccessClaim() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.RELEASED, PaymentState.REFUNDED, null),
                "wf-reported-SUCCESS",
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(r.pattern()).isEqualTo(OraclePattern.F2);
    }

    @Test
    void ind3_unknownPayment_ignoresLlmSuccessTranscript() {
        UUID orderId = UUID.randomUUID();
        // trialId stands in for archived LLM transcript id — must not flip classification
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.RELEASED, PaymentState.UNKNOWN, null),
                "wf",
                "llm-transcript-says-order-completed");
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.UNKNOWN_PAYMENT);
        assertThat(r.trialId()).isEqualTo("llm-transcript-says-order-completed");
    }

    @Test
    void ind4_danglingCapturedHeld_neverConfirmedFailure() {
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.HELD, PaymentState.CAPTURED, null),
                "wf-reported-COMPENSATED_FAILURE",
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(r.reasonCode()).isEqualTo(OracleReasonCode.COMPENSATION_INCOMPLETE);
        assertThat(r.classification()).isNotEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
    }

    @Test
    void ind5_noTemporal_pureFixtures() {
        // This test class never starts Temporal; classifierOnly forbids JDBC.
        assertThat(oracle.evaluateSnapshot(
                        SnapshotFixtures.snapshot(UUID.randomUUID(), null, null, null), null, null)
                .classification())
                .isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
    }

    @Test
    void ind6_apiCrossCheckOutOfScope_dbPrimaryOnly() {
        // TH-401-B HTTP cross-check is out of scope; primary classification uses snapshot only.
        UUID orderId = UUID.randomUUID();
        OracleResult r = oracle.evaluateSnapshot(
                SnapshotFixtures.snapshot(
                        orderId, ReservationState.CONSUMED, PaymentState.CAPTURED, ShipmentState.ARRANGED),
                null,
                null);
        assertThat(r.classification()).isEqualTo(BusinessOutcome.CONFIRMED_SUCCESS);
        assertThat(r.evaluatorVersion()).isEqualTo(OracleResult.EVALUATOR_VERSION);
    }
}
