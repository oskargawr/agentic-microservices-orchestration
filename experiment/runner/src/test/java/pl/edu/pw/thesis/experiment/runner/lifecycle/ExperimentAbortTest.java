package pl.edu.pw.thesis.experiment.runner.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentStatusRecord;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;

/** OWN-P3 — experiment-level abort does not rewrite terminal trials. */
class ExperimentAbortTest {

    @TempDir Path tmp;

    @Test
    void abortWritesExperimentStatusSeparatelyAndLeavesTerminalTrial() throws Exception {
        ExperimentDefinition exp = experiment("exp-abort");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);

        UUID orderId = UUID.randomUUID();
        TrialRecord terminal = new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                exp.experimentId(),
                "trial-1",
                exp.protocolId(),
                exp.phase(),
                "A",
                "F0",
                0,
                orderId,
                exp.businessDefaults(),
                exp.gitSha(),
                Instant.parse("2026-11-02T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "order-" + orderId,
                "run-1",
                "COMPLETED",
                HarnessStatus.EXCLUDED,
                false,
                null,
                "TEST",
                null,
                null,
                null,
                null,
                "x");
        store.saveTrial(terminal);

        ExperimentStatusRecord aborted = ExperimentAbort.abortExperiment(
                store, exp.experimentId(), "OPERATOR_INTERRUPT", Instant.parse("2026-11-02T01:00:00Z"));

        assertThat(aborted.harnessStatus()).isEqualTo(HarnessStatus.ABORTED_EXPERIMENT);
        assertThat(store.loadExperimentStatus().harnessStatus())
                .isEqualTo(HarnessStatus.ABORTED_EXPERIMENT);
        assertThat(store.hasTerminalArtifact("trial-1")).isTrue();

        ExperimentStatusRecord again = ExperimentAbort.abortExperiment(
                store, exp.experimentId(), "SECOND", Instant.parse("2026-11-02T02:00:00Z"));
        assertThat(again.reason()).isEqualTo("OPERATOR_INTERRUPT");
    }

    private static ExperimentDefinition experiment(String id) {
        return new ExperimentDefinition(
                id,
                "protocol-v0",
                "pilot",
                List.of("A"),
                List.of("F0"),
                1,
                BusinessInputs.protocolPilotDefaults(),
                RunnerTiming.protocolDefaults(),
                "sha",
                Instant.parse("2026-11-02T00:00:00Z"));
    }
}
