package pl.edu.pw.thesis.experiment.runner.accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.experiment.runner.model.ExclusionCategory;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.OracleSummary;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

class ExpectedCellOutcomeTest {

    @Test
    void ownerSuccessMapsToConfirmedSuccess() {
        assertThat(ExpectedCellOutcome.forProfile("F0").expectedClassification())
                .isEqualTo(BusinessOutcome.CONFIRMED_SUCCESS);
        assertThat(ExpectedCellOutcome.forProfile("F3-Recovered").expectedClassification())
                .isEqualTo(BusinessOutcome.CONFIRMED_SUCCESS);
    }

    @Test
    void failuresAndAmbiguous() {
        assertThat(ExpectedCellOutcome.forProfile("F1-P").expectedClassification())
                .isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(ExpectedCellOutcome.forProfile("F2").expectedClassification())
                .isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(ExpectedCellOutcome.forProfile("F3-Unresolved").expectedClassification())
                .isEqualTo(BusinessOutcome.AMBIGUOUS);
        assertThat(ExpectedCellOutcome.forProfile("F3-Unresolved").requireF3UnresolvedGate()).isTrue();
    }

    @Test
    void validationDetectsMismatchAndExclusion() {
        CellOutcomeValidation v = CellOutcomeValidation.evaluate(completed("t1", "F0", "CONFIRMED_FAILURE"));
        assertThat(v.matched()).isFalse();
        assertThat(v.detail()).contains("expectedClassification=CONFIRMED_SUCCESS");

        TrialRecord excluded = new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                "exp",
                "t2",
                "protocol-v0",
                "pilot",
                "A",
                "F3-Unresolved",
                0,
                UUID.randomUUID(),
                null,
                "sha",
                Instant.parse("2026-11-03T12:00:00Z"),
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
                "F3_UNRESOLVED_ACCEPTANCE_FAILED",
                null,
                null,
                null,
                null,
                "CANCELLED");
        assertThat(CellOutcomeValidation.evaluate(excluded).matched()).isFalse();
        assertThat(CellOutcomeValidation.evaluate(excluded).detail()).contains("not COMPLETED");
    }

    @Test
    void matrixSummaryExitCodes() {
        MatrixSummary ok = MatrixSummary.fromTrials(
                "exp",
                "protocol-v0",
                "sha",
                Instant.parse("2026-11-03T12:00:00Z"),
                1,
                List.of(completed("t0", "F0", "CONFIRMED_SUCCESS")));
        assertThat(ok.allMatched()).isTrue();
        assertThat(ok.suggestedExitCode()).isEqualTo(0);

        MatrixSummary badClass = MatrixSummary.fromTrials(
                "exp",
                "protocol-v0",
                "sha",
                Instant.parse("2026-11-03T12:00:00Z"),
                1,
                List.of(completed("t0", "F0", "AMBIGUOUS")));
        assertThat(badClass.suggestedExitCode()).isEqualTo(3);
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
                Instant.parse("2026-11-03T12:00:00Z"),
                Instant.parse("2026-11-03T12:00:01Z"),
                null,
                Instant.parse("2026-11-03T12:00:10Z"),
                Instant.parse("2026-11-03T12:00:11Z"),
                Instant.parse("2026-11-03T12:00:12Z"),
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
                        Instant.parse("2026-11-03T12:00:12Z")),
                classification,
                null,
                100,
                null);
    }
}
