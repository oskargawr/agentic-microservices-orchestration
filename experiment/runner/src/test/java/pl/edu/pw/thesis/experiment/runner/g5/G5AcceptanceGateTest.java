package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/**
 * G5-3 acceptance must gate the test: write durable artifact first, then fail when INVALID.
 * TrialRecord remains immutable.
 */
class G5AcceptanceGateTest {

    @TempDir Path tmp;

    @Test
    void invalidAcceptance_writesArtifactThenRequireValidFails_withoutMutatingTrial() throws Exception {
        TrialRecord trial = immutableTrial();

        G5HistoryValidationResult bad = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(2, G5HistoryEventKind.WORKFLOW_TASK_STARTED),
                new G5HistoryEventView(8, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));

        G5AcceptanceRecord acceptance = G5AcceptanceRecord.fromValidation(
                trial.workflowId(),
                trial.runId(),
                trial.gitSha(),
                "PASS",
                "describeTaskQueue pollers=0",
                "GetWorkflowExecutionHistory",
                tmp.resolve("history.tsv").toString(),
                bad,
                Instant.parse("2026-11-03T14:00:00Z"));

        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);

        Path written = new G5AcceptanceWriter().write(tmp, acceptance);
        assertThat(written).exists();
        assertThat(Files.readString(written)).contains("\"acceptanceResult\" : \"INVALID\"");

        G5AcceptanceWriter.assertUnchanged(trial, trial);
        assertThat(trial.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(trial.terminatedBy()).isEqualTo(TerminatedBy.T_RUN_MAX);

        assertThatThrownBy(acceptance::requireValidForG5Evidence)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("INVALID");

        // Artifact still present after the failing gate
        assertThat(written).exists();
        G5AcceptanceRecord roundTrip = new G5AcceptanceWriter().read(written);
        assertThat(roundTrip.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
    }

    @Test
    void validAcceptance_requireValidPasses() {
        G5HistoryValidationResult ok = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(1, G5HistoryEventKind.OTHER),
                new G5HistoryEventView(4, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));
        G5AcceptanceRecord acceptance = G5AcceptanceRecord.fromValidation(
                "order-1",
                "run-1",
                "sha",
                "PASS",
                "ok",
                "GetWorkflowExecutionHistory",
                "hist.tsv",
                ok,
                Instant.parse("2026-11-03T14:00:00Z"));
        acceptance.requireValidForG5Evidence();
    }

    @Test
    void incompleteFetch_yieldsInvalid() {
        G5HistoryFetchResult incomplete = G5HistoryFetchResult.incomplete(
                "wf", "run", List.of(), 1, "page failed");
        G5AcceptanceRecord acceptance = G5AcceptanceRecord.fromValidation(
                "wf",
                "run",
                "sha",
                "PASS",
                "ok",
                "GetWorkflowExecutionHistory",
                "hist.tsv",
                G5HistoryGate.validateFetch(incomplete),
                Instant.parse("2026-11-03T14:00:00Z"));
        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.INVALID);
        assertThat(acceptance.historyComplete()).isFalse();
    }

    private static TrialRecord immutableTrial() {
        return new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                "exp-g5",
                "trial-g5-3",
                "protocol-v0",
                "pilot",
                "A",
                "F0",
                0,
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                null,
                "deadbeef",
                Instant.parse("2026-11-03T12:00:00Z"),
                Instant.parse("2026-11-03T12:00:01Z"),
                null,
                Instant.parse("2026-11-03T12:02:01Z"),
                Instant.parse("2026-11-03T12:02:31Z"),
                Instant.parse("2026-11-03T12:02:32Z"),
                TerminatedBy.T_RUN_MAX,
                true,
                "order-11111111-1111-1111-1111-111111111111",
                "run-abc",
                "TERMINATED",
                HarnessStatus.COMPLETED,
                true,
                null,
                null,
                null,
                null,
                null,
                100,
                null);
    }
}
