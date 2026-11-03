package pl.edu.pw.thesis.experiment.runner.accept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.experiment.runner.model.ExclusionCategory;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.OracleSummary;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

class MatrixSummaryTest {

    private static final Instant T = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void completeMatrix_allMatched() {
        MatrixSummary s = MatrixSummary.fromTrials(
                "exp",
                "protocol-v0",
                "sha",
                T,
                2,
                List.of(
                        completed("t0", "F0", "CONFIRMED_SUCCESS"),
                        completed("t1", "F1-P", "CONFIRMED_FAILURE")));
        assertThat(s.plannedCells()).isEqualTo(2);
        assertThat(s.executedCells()).isEqualTo(2);
        assertThat(s.matched()).isEqualTo(2);
        assertThat(s.mismatched()).isZero();
        assertThat(s.excluded()).isZero();
        assertThat(s.notExecuted()).isZero();
        assertThat(s.allMatched()).isTrue();
        assertThat(s.suggestedExitCode()).isEqualTo(0);
    }

    @Test
    void earlyAbort_afterFirstCell_reportsNotExecuted() {
        MatrixSummary s = MatrixSummary.fromTrials(
                "exp",
                "protocol-v0",
                "sha",
                T,
                4,
                List.of(completed("t0", "F0", "CONFIRMED_SUCCESS")));
        assertThat(s.plannedCells()).isEqualTo(4);
        assertThat(s.executedCells()).isEqualTo(1);
        assertThat(s.matched()).isEqualTo(1);
        assertThat(s.notExecuted()).isEqualTo(3);
        assertThat(s.allMatched()).isFalse();
        assertThat(s.suggestedExitCode()).isEqualTo(1);
        assertThat(s.cells()).hasSize(1);
    }

    @Test
    void harnessExclusion_countedSeparately() {
        MatrixSummary s = MatrixSummary.fromTrials(
                "exp",
                "protocol-v0",
                "sha",
                T,
                1,
                List.of(excluded("t-ex", "F0")));
        assertThat(s.matched()).isZero();
        assertThat(s.excluded()).isEqualTo(1);
        assertThat(s.mismatched()).isZero();
        assertThat(s.notExecuted()).isZero();
        assertThat(s.allMatched()).isFalse();
        assertThat(s.suggestedExitCode()).isEqualTo(1);
    }

    @Test
    void classificationMismatch_exitCodeThree() {
        MatrixSummary s = MatrixSummary.fromTrials(
                "exp",
                "protocol-v0",
                "sha",
                T,
                1,
                List.of(completed("t0", "F0", "AMBIGUOUS")));
        assertThat(s.mismatched()).isEqualTo(1);
        assertThat(s.excluded()).isZero();
        assertThat(s.allMatched()).isFalse();
        assertThat(s.suggestedExitCode()).isEqualTo(3);
    }

    @Test
    void emptyOrInvalid_plannedZero_orEmptyTrials() {
        MatrixSummary emptyPlan = MatrixSummary.fromTrials(
                "exp", "protocol-v0", "sha", T, 0, List.of());
        assertThat(emptyPlan.plannedCells()).isZero();
        assertThat(emptyPlan.allMatched()).isFalse();
        assertThat(emptyPlan.suggestedExitCode()).isEqualTo(1);

        MatrixSummary plannedButNoneRun = MatrixSummary.fromTrials(
                "exp", "protocol-v0", "sha", T, 3, List.of());
        assertThat(plannedButNoneRun.notExecuted()).isEqualTo(3);
        assertThat(plannedButNoneRun.executedCells()).isZero();
        assertThat(plannedButNoneRun.allMatched()).isFalse();
        assertThat(plannedButNoneRun.suggestedExitCode()).isEqualTo(1);

        assertThatThrownBy(() -> MatrixSummary.fromTrials(
                        "exp", "protocol-v0", "sha", T, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TrialRecord completed(String trialId, String profile, String classification) {
        return new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                "exp",
                trialId,
                "protocol-v0",
                "pilot",
                "A",
                profile,
                0,
                UUID.randomUUID(),
                null,
                "sha",
                T,
                T.plusSeconds(1),
                null,
                T.plusSeconds(10),
                T.plusSeconds(11),
                T.plusSeconds(12),
                null,
                true,
                "wf",
                "run",
                "COMPLETED",
                HarnessStatus.COMPLETED,
                true,
                null,
                null,
                new OracleSummary(
                        classification,
                        "NONE",
                        "NONE",
                        List.of(),
                        List.of(),
                        "th-401-a-1",
                        T.plusSeconds(12)),
                classification,
                null,
                100,
                null);
    }

    private static TrialRecord excluded(String trialId, String profile) {
        return new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                "exp",
                trialId,
                "protocol-v0",
                "pilot",
                "A",
                profile,
                0,
                UUID.randomUUID(),
                null,
                "sha",
                T,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                HarnessStatus.EXCLUDED,
                false,
                ExclusionCategory.TECHNICAL,
                "FAULT_VERIFY_FAILED",
                null,
                null,
                null,
                null,
                "CANCELLED");
    }
}
