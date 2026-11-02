package pl.edu.pw.thesis.experiment.runner.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.OracleSummary;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

class FileResultStoreTest {

    @TempDir Path tmp;

    @Test
    void atomicSaveAndCsvExport() throws Exception {
        ExperimentDefinition exp = experiment("exp-1");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);

        TrialRecord record = excluded(exp, "trial-1", UUID.randomUUID());
        store.saveTrial(record);

        Path json = tmp.resolve("pilot/exp-1/trials/trial-1.json");
        assertThat(Files.exists(json)).isTrue();
        assertThat(Files.exists(tmp.resolve("pilot/exp-1/trials.csv"))).isTrue();
        assertThat(store.hasTerminalArtifact("trial-1")).isTrue();
    }

    @Test
    void refusesOverwriteTerminalWithDifferentExecution() throws Exception {
        ExperimentDefinition exp = experiment("exp-2");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);

        UUID order1 = UUID.randomUUID();
        store.saveTrial(excluded(exp, "trial-x", order1));

        TrialRecord other = excluded(exp, "trial-x", UUID.randomUUID());
        assertThatThrownBy(() -> store.saveTrial(other)).hasMessageContaining("Refuse overwrite");
    }

    @Test
    void refusesDowngradeOfDurableCompleted() throws Exception {
        ExperimentDefinition exp = experiment("exp-down");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);
        UUID order = UUID.randomUUID();
        store.saveTrial(completed(exp, "trial-down", order));

        assertThatThrownBy(() -> store.saveTrial(excluded(exp, "trial-down", order)))
                .hasMessageContaining("Refuse downgrade of durable COMPLETED");
        assertThat(store.readTrial("trial-down").harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
    }

    @Test
    void csvRebuildIsIdempotentAcrossRepeatedSaves() throws Exception {
        ExperimentDefinition exp = experiment("exp-csv");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);

        UUID order = UUID.randomUUID();
        TrialRecord record = excluded(exp, "trial-csv", order);
        store.saveTrial(record);
        store.saveTrial(record);

        String csv = Files.readString(tmp.resolve("pilot/exp-csv/trials.csv"));
        long dataRows = csv.lines().skip(1).filter(l -> !l.isBlank()).count();
        assertThat(dataRows).isEqualTo(1);
        assertThat(csv.lines().filter(l -> l.contains("trial-csv")).count()).isEqualTo(1);
    }

    @Test
    void csvFailureAfterJsonCommitDoesNotFailSaveOrDowngrade() throws Exception {
        ExperimentDefinition exp = experiment("exp-csv-fail");
        AtomicInteger rebuilds = new AtomicInteger();
        FileResultStore store = new FileResultStore(tmp, exp) {
            @Override
            public void rebuildCsvFromJsonTrials() throws IOException {
                if (rebuilds.getAndIncrement() == 0) {
                    throw new IOException("csv boom");
                }
                super.rebuildCsvFromJsonTrials();
            }
        };
        store.saveExperiment(exp);
        UUID order = UUID.randomUUID();
        TrialRecord completed = completed(exp, "trial-ok", order);

        store.saveTrial(completed);

        TrialRecord durable = store.readTrial("trial-ok");
        assertThat(durable.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(durable.oracle().classification()).isEqualTo("CONFIRMED_SUCCESS");

        assertThatThrownBy(() -> store.saveTrial(excluded(exp, "trial-ok", order)))
                .hasMessageContaining("Refuse downgrade");

        store.rebuildCsvFromJsonTrials();
        String csv = Files.readString(store.trialsCsvPath());
        assertThat(csv.lines().filter(l -> l.contains("trial-ok")).count()).isEqualTo(1);
        assertThat(csv).contains("COMPLETED");
    }

    @Test
    void malformedCanonicalJsonFailsClosedDuringCsvRebuild() throws Exception {
        ExperimentDefinition exp = experiment("exp-bad-json");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);
        Path trials = tmp.resolve("pilot/exp-bad-json/trials");
        Files.createDirectories(trials);
        Files.writeString(trials.resolve("broken.json"), "{not-json");

        assertThatThrownBy(store::rebuildCsvFromJsonTrials)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Malformed canonical trial JSON");
    }

    @Test
    void oracleConfirmedPartialPreservedSeparately() throws Exception {
        ExperimentDefinition exp = experiment("exp-partial");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);
        TrialRecord candidate = excluded(exp, "trial-p", UUID.randomUUID());
        store.saveOracleConfirmedPartial(candidate);
        assertThat(Files.exists(store.trialPartialPath("trial-p"))).isTrue();
        assertThat(Files.exists(store.trialJsonPath("trial-p"))).isFalse();
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
                "abc",
                Instant.parse("2026-11-02T00:00:00Z"));
    }

    private static TrialRecord excluded(ExperimentDefinition exp, String trialId, UUID orderId) {
        return new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                exp.experimentId(),
                trialId,
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
                null,
                null,
                HarnessStatus.EXCLUDED,
                false,
                null,
                "TEST",
                null,
                null,
                null,
                null,
                "test");
    }

    private static TrialRecord completed(ExperimentDefinition exp, String trialId, UUID orderId) {
        return new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                exp.experimentId(),
                trialId,
                exp.protocolId(),
                exp.phase(),
                "A",
                "F0",
                0,
                orderId,
                exp.businessDefaults(),
                exp.gitSha(),
                Instant.parse("2026-11-02T00:00:00Z"),
                Instant.parse("2026-11-02T00:00:01Z"),
                null,
                Instant.parse("2026-11-02T00:00:10Z"),
                Instant.parse("2026-11-02T00:00:40Z"),
                Instant.parse("2026-11-02T00:00:41Z"),
                TerminatedBy.WORKFLOW_STOP,
                true,
                "order-" + orderId,
                "run-1",
                "COMPLETED",
                HarnessStatus.COMPLETED,
                true,
                null,
                null,
                new OracleSummary(
                        "CONFIRMED_SUCCESS",
                        "SUCCESS",
                        "NONE",
                        List.of(),
                        List.of(),
                        "test-eval",
                        Instant.parse("2026-11-02T00:00:41Z")),
                "SUCCESS",
                null,
                100,
                null);
    }
}
