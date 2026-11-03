package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

class G5AcceptanceWriterTest {

    @TempDir Path tmp;

    @Test
    void writesAndReadsAcceptanceJson_withoutMutatingTrialRecord() throws Exception {
        TrialRecord trial = sampleTrial(HarnessStatus.COMPLETED, TerminatedBy.T_RUN_MAX, "TERMINATED");
        TrialRecord snapshot = trial;

        G5HistoryValidationResult validation = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(1, G5HistoryEventKind.OTHER),
                new G5HistoryEventView(4, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));

        G5AcceptanceRecord acceptance = G5AcceptanceRecord.fromValidation(
                trial.workflowId(),
                trial.runId(),
                trial.gitSha(),
                "PASS",
                "taskQueue=variant-a-order-fulfillment; pollerDetected=false",
                "GetWorkflowExecutionHistory",
                tmp.resolve("history.tsv").toString(),
                validation,
                Instant.parse("2026-11-03T14:00:00Z"));

        Path path = new G5AcceptanceWriter().write(tmp, acceptance);
        assertThat(path.getFileName().toString()).isEqualTo("g5-3-acceptance.json");
        assertThat(path).exists();

        G5AcceptanceRecord roundTrip = new G5AcceptanceWriter().read(path);
        assertThat(roundTrip.acceptanceResult()).isEqualTo("VALID");
        assertThat(roundTrip.scenarioId()).isEqualTo("G5-3");
        assertThat(roundTrip.workflowId()).isEqualTo(trial.workflowId());

        G5AcceptanceWriter.assertUnchanged(snapshot, trial);
        assertThat(trial.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(trial.temporalStatus()).isEqualTo("TERMINATED");
        assertThat(trial.terminatedBy()).isEqualTo(TerminatedBy.T_RUN_MAX);
    }

    @Test
    void completedTrialMayHaveInvalidAcceptance() {
        G5HistoryValidationResult bad = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(2, G5HistoryEventKind.WORKFLOW_TASK_STARTED),
                new G5HistoryEventView(8, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));
        G5AcceptanceRecord acceptance = G5AcceptanceRecord.fromValidation(
                "order-1",
                "run-1",
                "sha",
                "PASS",
                "ok",
                "GetWorkflowExecutionHistory",
                "hist.tsv",
                bad,
                Instant.parse("2026-11-03T14:00:00Z"));
        assertThat(acceptance.acceptanceResult()).isEqualTo("INVALID");
        assertThat(acceptance.invalidationReason()).isNotBlank();
    }

    @Test
    void assertUnchanged_detectsMutation() {
        TrialRecord a = sampleTrial(HarnessStatus.COMPLETED, TerminatedBy.T_RUN_MAX, "TERMINATED");
        TrialRecord b = sampleTrial(HarnessStatus.EXCLUDED, TerminatedBy.T_RUN_MAX, "TERMINATED");
        assertThatThrownBy(() -> G5AcceptanceWriter.assertUnchanged(a, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable");
    }

    private static TrialRecord sampleTrial(
            HarnessStatus harness, TerminatedBy terminatedBy, String temporalStatus) {
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
                true,
                "order-" + orderId,
                "run-abc",
                temporalStatus,
                harness,
                harness == HarnessStatus.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                100,
                null);
    }
}
