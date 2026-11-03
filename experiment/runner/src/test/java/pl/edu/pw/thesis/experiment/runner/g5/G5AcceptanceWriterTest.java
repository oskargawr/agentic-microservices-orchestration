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
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.OracleSummary;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;

class G5AcceptanceWriterTest {

    @TempDir Path tmp;

    @Test
    void writesAndReadsAcceptanceJson_withoutMutatingTrialRecord() throws Exception {
        TrialRecord trial = sampleTrial(HarnessStatus.COMPLETED, TerminatedBy.T_RUN_MAX, "TERMINATED", true);
        TrialRecord snapshot = trial;
        ExperimentDefinition exp = new ExperimentDefinition(
                "exp-g5",
                "protocol-v0",
                "pilot",
                List.of("A"),
                List.of("F0"),
                1,
                BusinessInputs.protocolPilotDefaults(),
                RunnerTiming.protocolDefaults(),
                "deadbeef",
                Instant.parse("2026-11-03T12:00:00Z"));
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);
        store.saveTrial(trial);
        Path durable = store.trialJsonPath(trial.trialId());
        TrialRecord durableRead = store.readTrial(trial.trialId());
        assertThat(durableRead).isNotSameAs(trial).isEqualTo(trial);

        G5HistoryValidationResult validation = G5HistoryGate.validate(List.of(
                new G5HistoryEventView(1, G5HistoryEventKind.OTHER),
                new G5HistoryEventView(4, G5HistoryEventKind.WORKFLOW_EXECUTION_TERMINATED)));

        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                trial,
                durable,
                durableRead,
                1,
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
        assertThat(roundTrip.trialHarnessStatus()).isEqualTo("COMPLETED");

        G5AcceptanceWriter.assertUnchanged(snapshot, trial);
        assertThat(trial.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(trial.temporalStatus()).isEqualTo("TERMINATED");
        assertThat(trial.terminatedBy()).isEqualTo(TerminatedBy.T_RUN_MAX);
    }

    @Test
    void completedTrialMayHaveInvalidAcceptance() throws Exception {
        TrialRecord trial = sampleTrial(HarnessStatus.COMPLETED, TerminatedBy.T_RUN_MAX, "TERMINATED", true);
        Path durable = tmp.resolve("trial.json");
        Files.writeString(durable, "{}");
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
                "GetWorkflowExecutionHistory",
                "hist.tsv",
                bad,
                Instant.parse("2026-11-03T14:00:00Z"));
        assertThat(acceptance.acceptanceResult()).isEqualTo("INVALID");
        assertThat(acceptance.invalidationReason()).isNotBlank();
    }

    @Test
    void assertUnchanged_detectsMutation() {
        TrialRecord a = sampleTrial(HarnessStatus.COMPLETED, TerminatedBy.T_RUN_MAX, "TERMINATED", true);
        TrialRecord b = sampleTrial(HarnessStatus.EXCLUDED, TerminatedBy.T_RUN_MAX, "TERMINATED", false);
        assertThatThrownBy(() -> G5AcceptanceWriter.assertUnchanged(a, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable");
    }

    private static TrialRecord sampleTrial(
            HarnessStatus harness, TerminatedBy terminatedBy, String temporalStatus, boolean quiesced) {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OracleSummary oracle = harness == HarnessStatus.COMPLETED
                ? new OracleSummary(
                        "CONFIRMED_FAILURE",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        "th-401-a-1",
                        Instant.parse("2026-11-03T12:02:32Z"))
                : null;
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
                null,
                null,
                oracle,
                null,
                null,
                100,
                null);
    }
}
