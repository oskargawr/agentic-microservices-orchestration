package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.OracleSummary;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/**
 * G5-3 acceptance must require COMPLETED TrialRecord outcome + history + preflight.
 * Write durable artifact first, then fail when INVALID. TrialRecord remains immutable.
 */
class G5AcceptanceGateTest {

    @TempDir Path tmp;

    @Test
    void completedQuiescedOracleOnceDurable_andValidHistory_isValid() throws Exception {
        TrialRecord trial = completedTerminatedTrial();
        Path durable = tmp.resolve("trial.json");
        Files.writeString(durable, "{\"placeholder\":true}");
        // durableMatches uses Objects.equals(measured, durableRead) — pass same instance as read
        G5HistoryValidationResult okHistory = validHistory();

        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                trial,
                durable,
                trial,
                1,
                "PASS",
                "DescribeTaskQueue pollers=0",
                "GetWorkflowExecutionHistory(paginated)",
                tmp.resolve("history.tsv").toString(),
                okHistory,
                Instant.parse("2026-11-03T14:00:00Z"));

        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.VALID);
        assertThat(acceptance.trialOutcomeValidation()).isEqualTo("PASS");
        Path written = new G5AcceptanceWriter().write(tmp, acceptance);
        assertThat(written).exists();
        G5AcceptanceWriter.assertUnchanged(trial, trial);
        acceptance.requireValidForG5Evidence();
    }

    @Test
    void excludedTrialRecord_isInvalid_evenWithValidHistory() throws Exception {
        TrialRecord trial = excludedTrial();
        Path durable = tmp.resolve("trial.json");
        Files.writeString(durable, "x");
        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                trial,
                durable,
                trial,
                0,
                "PASS",
                "ok",
                "GetWorkflowExecutionHistory(paginated)",
                "hist.tsv",
                validHistory(),
                Instant.parse("2026-11-03T14:00:00Z"));

        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
        assertThat(acceptance.invalidationReason()).contains("EXCLUDED");
        Path written = new G5AcceptanceWriter().write(tmp, acceptance);
        assertThat(written).exists();
        assertThatThrownBy(acceptance::requireValidForG5Evidence).isInstanceOf(AssertionError.class);
        G5AcceptanceWriter.assertUnchanged(trial, trial);
    }

    @Test
    void missingOracleSummary_isInvalid() {
        TrialRecord trial = completedTerminatedTrialWithoutOracle();
        Path durable = tmp.resolve("t.json");
        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                trial,
                durable,
                trial,
                1,
                "PASS",
                "ok",
                "hist",
                "hist.tsv",
                validHistory(),
                Instant.parse("2026-11-03T14:00:00Z"));
        // Also missing durable file
        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
        assertThat(acceptance.invalidationReason()).contains("oracle summary missing");
    }

    @Test
    void oracleInvocationCountOtherThanOne_isInvalid() throws Exception {
        TrialRecord trial = completedTerminatedTrial();
        Path durable = tmp.resolve("trial.json");
        Files.writeString(durable, "x");
        G5AcceptanceRecord zero = G5AcceptanceEvaluator.evaluate(
                trial, durable, trial, 0, "PASS", "ok", "h", "h.tsv", validHistory(), Instant.parse("2026-11-03T14:00:00Z"));
        G5AcceptanceRecord two = G5AcceptanceEvaluator.evaluate(
                trial, durable, trial, 2, "PASS", "ok", "h", "h.tsv", validHistory(), Instant.parse("2026-11-03T14:00:00Z"));
        assertThat(zero.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
        assertThat(zero.invalidationReason()).contains("oracleEvaluateCalls=0");
        assertThat(two.invalidationReason()).contains("oracleEvaluateCalls=2");
    }

    @Test
    void missingDurableTrialJson_isInvalid() {
        TrialRecord trial = completedTerminatedTrial();
        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                trial,
                tmp.resolve("missing.json"),
                null,
                1,
                "PASS",
                "ok",
                "h",
                "h.tsv",
                validHistory(),
                Instant.parse("2026-11-03T14:00:00Z"));
        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
        assertThat(acceptance.invalidationReason()).contains("durable TrialRecord JSON missing");
    }

    @Test
    void nonQuiescedTrialRecord_isInvalid() throws Exception {
        TrialRecord trial = completedTerminatedTrial(false);
        Path durable = tmp.resolve("trial.json");
        Files.writeString(durable, "x");
        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                trial, durable, trial, 1, "PASS", "ok", "h", "h.tsv", validHistory(), Instant.parse("2026-11-03T14:00:00Z"));
        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
        assertThat(acceptance.invalidationReason()).contains("workflowQuiesced=false");
    }

    @Test
    void acceptanceEvaluation_doesNotMutateTrialRecord() throws Exception {
        TrialRecord before = completedTerminatedTrial();
        TrialRecord snapshot = before;
        Path durable = tmp.resolve("trial.json");
        Files.writeString(durable, "x");
        G5AcceptanceEvaluator.evaluate(
                before, durable, before, 1, "PASS", "ok", "h", "h.tsv", validHistory(), Instant.parse("2026-11-03T14:00:00Z"));
        G5AcceptanceWriter.assertUnchanged(snapshot, before);
        assertThat(before.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(before.oracle()).isNotNull();
    }

    @Test
    void historyOnlyFromValidation_isNeverValid() {
        @SuppressWarnings("deprecation")
        G5AcceptanceRecord acceptance = G5AcceptanceRecord.fromValidation(
                "order-1",
                "run-1",
                "sha",
                "PASS",
                "ok",
                "GetWorkflowExecutionHistory",
                "hist.tsv",
                validHistory(),
                Instant.parse("2026-11-03T14:00:00Z"));
        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
        assertThatThrownBy(acceptance::requireValidForG5Evidence).isInstanceOf(AssertionError.class);
    }

    @Test
    void invalidHistory_writesArtifactThenFails_withoutMutatingTrial() throws Exception {
        TrialRecord trial = completedTerminatedTrial();
        Path durable = tmp.resolve("trial.json");
        Files.writeString(durable, "x");
        G5HistoryValidationResult bad = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(2, G5HistoryEventKind.WORKFLOW_TASK_STARTED),
                new G5HistoryEventView(8, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));

        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                trial,
                durable,
                trial,
                1,
                "PASS",
                "ok",
                "h",
                "h.tsv",
                bad,
                Instant.parse("2026-11-03T14:00:00Z"));
        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
        Path written = new G5AcceptanceWriter().write(tmp, acceptance);
        assertThat(written).exists();
        assertThatThrownBy(acceptance::requireValidForG5Evidence).isInstanceOf(AssertionError.class);
        G5AcceptanceWriter.assertUnchanged(trial, trial);
    }

    private static G5HistoryValidationResult validHistory() {
        return G5HistoryGate.validate(List.of(
                new G5HistoryEventView(1, G5HistoryEventKind.OTHER),
                new G5HistoryEventView(4, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));
    }

    private static TrialRecord completedTerminatedTrial() {
        return completedTerminatedTrial(true);
    }

    private static TrialRecord completedTerminatedTrial(boolean quiesced) {
        return trial(
                HarnessStatus.COMPLETED,
                TerminatedBy.T_RUN_MAX,
                "TERMINATED",
                quiesced,
                new OracleSummary(
                        "CONFIRMED_FAILURE",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        "th-401-a-1",
                        Instant.parse("2026-11-03T12:02:32Z")));
    }

    private static TrialRecord completedTerminatedTrialWithoutOracle() {
        return trial(HarnessStatus.COMPLETED, TerminatedBy.T_RUN_MAX, "TERMINATED", true, null);
    }

    private static TrialRecord excludedTrial() {
        return trial(HarnessStatus.EXCLUDED, TerminatedBy.T_RUN_MAX, "TERMINATED", false, null);
    }

    private static TrialRecord trial(
            HarnessStatus harness,
            TerminatedBy terminatedBy,
            String temporalStatus,
            boolean quiesced,
            OracleSummary oracle) {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                "exp-g5",
                "trial-g5-3",
                "protocol-v0",
                "pilot",
                "A",
                "F0",
                0,
                orderId,
                null,
                "deadbeef",
                Instant.parse("2026-11-03T12:00:00Z"),
                Instant.parse("2026-11-03T12:00:01Z"),
                null,
                Instant.parse("2026-11-03T12:02:01Z"),
                Instant.parse("2026-11-03T12:02:31Z"),
                Instant.parse("2026-11-03T12:02:32Z"),
                terminatedBy,
                quiesced,
                "order-" + orderId,
                "run-abc",
                temporalStatus,
                harness,
                harness == HarnessStatus.COMPLETED,
                harness == HarnessStatus.EXCLUDED
                        ? pl.edu.pw.thesis.experiment.runner.model.ExclusionCategory.TECHNICAL
                        : null,
                harness == HarnessStatus.EXCLUDED ? "WORKFLOW_NOT_QUIESCED" : null,
                oracle,
                null,
                null,
                100,
                null);
    }
}
