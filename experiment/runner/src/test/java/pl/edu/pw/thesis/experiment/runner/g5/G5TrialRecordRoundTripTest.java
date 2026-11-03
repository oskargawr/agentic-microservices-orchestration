package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.OracleSummary;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TerminatedBy;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;

/**
 * Non-live reproduction of the G5-3 INVALID cause: durable JSON must round-trip to a
 * {@link TrialRecord} that equals the measured value, including {@link ArmedFaultRecord}.
 */
class G5TrialRecordRoundTripTest {

    @TempDir Path tmp;

    @Test
    void g5StyleCompletedTerminated_withArmedFault_roundTripEquals_andAcceptanceValid()
            throws Exception {
        ExperimentDefinition exp = new ExperimentDefinition(
                "g5-3-rt",
                "protocol-v0",
                "pre-pilot",
                List.of("A"),
                List.of("F0"),
                1,
                BusinessInputs.protocolPilotDefaults(),
                RunnerTiming.protocolDefaults(),
                "55a84fbe9b795ce3137b4a6831800d35f21670e0",
                Instant.parse("2026-11-03T15:33:34Z"));
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);

        ArmedFaultRecord armed = new ArmedFaultRecord(
                "F0",
                "NONE",
                Map.of("injection", "none"),
                Instant.parse("2026-11-03T15:33:34.984134Z"));

        UUID orderId = UUID.fromString("f5a0d000-59eb-4184-8b3f-db2ac84ca28e");
        TrialRecord measured = new TrialRecord(
                TrialRecord.SCHEMA_VERSION,
                exp.experimentId(),
                "trial-g5-3",
                exp.protocolId(),
                exp.phase(),
                "A",
                "F0",
                0,
                orderId,
                exp.businessDefaults(),
                exp.gitSha(),
                Instant.parse("2026-11-03T15:33:34.955632Z"),
                Instant.parse("2026-11-03T15:33:34.984134Z"),
                null,
                Instant.parse("2026-11-03T15:35:35.093686Z"),
                Instant.parse("2026-11-03T15:36:05.098367Z"),
                Instant.parse("2026-11-03T15:36:05.123066Z"),
                TerminatedBy.T_RUN_MAX,
                true,
                "order-" + orderId,
                "882c0f0a-b766-4632-9f67-f591fb803cc6",
                "TERMINATED",
                HarnessStatus.COMPLETED,
                true,
                null,
                null,
                new OracleSummary(
                        "CONFIRMED_FAILURE",
                        "F0",
                        "NONE",
                        List.of(),
                        List.of(),
                        "th-401-a-1",
                        Instant.parse("2026-11-03T15:36:05.123066Z")),
                null,
                armed,
                100,
                null);

        store.saveTrial(measured);
        TrialRecord durable = store.readTrial(measured.trialId());

        assertThat(durable.faultArmedRecord()).isInstanceOf(ArmedFaultRecord.class);
        assertThat(durable.faultArmedRecord()).isEqualTo(armed);
        assertThat(durable).isEqualTo(measured);
        G5AcceptanceWriter.assertUnchanged(measured, durable);

        G5AcceptanceRecord acceptance = G5AcceptanceEvaluator.evaluate(
                measured,
                store.trialJsonPath(measured.trialId()),
                durable,
                1,
                "PASS",
                "taskQueue=variant-a-order-fulfillment; pollers=0",
                "GetWorkflowExecutionHistory(paginated)",
                tmp.resolve("history.tsv").toString(),
                new G5HistoryValidationResult(
                        true, true, false, true, Optional.of(3L), List.of(), "PASS"),
                Instant.parse("2026-11-03T15:36:05.238189Z"));

        assertThat(acceptance.durableTrialMatchesRunner()).isTrue();
        assertThat(acceptance.acceptanceResult()).isEqualTo(G5AcceptanceRecord.VALID);
        acceptance.requireValidForG5Evidence();
    }
}
