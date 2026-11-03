package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Backward-compat checks for typed {@link ArmedFaultRecord} on {@link TrialRecord}.
 *
 * <p>Historical fixture is a <strong>copy</strong> of live G5-3 evidence JSON; the evidence
 * directory itself is never modified by these tests.
 */
class G5HistoricalTrialRecordCompatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @TempDir Path tmp;

    @Test
    void nullFaultArmedRecord_roundTrips() throws Exception {
        ExperimentDefinition exp = experiment("exp-null-armed");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);

        TrialRecord measured = completed(exp, "trial-null", null);
        store.saveTrial(measured);
        TrialRecord durable = store.readTrial("trial-null");

        assertThat(durable.faultArmedRecord()).isNull();
        assertThat(durable).isEqualTo(measured);
        G5AcceptanceWriter.assertUnchanged(measured, durable);
    }

    @Test
    void historicalG5_3Json_deserializesToTypedArmedFaultRecord() throws Exception {
        TrialRecord durable;
        try (InputStream in = resource("g5/historical/trial-g5-3-historical.json")) {
            assertThat(in).as("classpath fixture must exist").isNotNull();
            durable = MAPPER.readValue(in, TrialRecord.class);
        }

        assertThat(durable.trialId()).isEqualTo("trial-g5-3");
        assertThat(durable.experimentId()).isEqualTo("g5-3-1793720014374");
        assertThat(durable.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(durable.temporalStatus()).isEqualTo("TERMINATED");
        assertThat(durable.terminatedBy()).isEqualTo(TerminatedBy.T_RUN_MAX);
        assertThat(durable.workflowQuiesced()).isTrue();
        assertThat(durable.workflowId()).isEqualTo("order-f5a0d000-59eb-4184-8b3f-db2ac84ca28e");
        assertThat(durable.runId()).isEqualTo("882c0f0a-b766-4632-9f67-f591fb803cc6");
        assertThat(durable.oracle()).isNotNull();
        assertThat(durable.oracle().classification()).isEqualTo("CONFIRMED_FAILURE");
        assertThat(durable.baselineOnHand()).isEqualTo(100);

        assertThat(durable.faultArmedRecord()).isInstanceOf(ArmedFaultRecord.class);
        ArmedFaultRecord armed = durable.faultArmedRecord();
        assertThat(armed.faultProfileId()).isEqualTo("F0");
        assertThat(armed.mechanism()).isEqualTo("NONE");
        assertThat(armed.effectiveConfig()).containsEntry("injection", "none");
        assertThat(armed.armedAt()).isEqualTo(Instant.parse("2026-11-03T15:33:34.984134Z"));

        // Re-serialize and re-read: nested map + Instant preserved; equals stable.
        byte[] again = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(durable);
        TrialRecord reread = MAPPER.readValue(again, TrialRecord.class);
        assertThat(reread).isEqualTo(durable);
        assertThat(reread.faultArmedRecord().effectiveConfig()).isEqualTo(armed.effectiveConfig());
        G5AcceptanceWriter.assertUnchanged(durable, reread);
    }

    @Test
    void f1pF2F3EffectiveConfigMaps_roundTripPreserveNestedStringValuesAndArmedAt() throws Exception {
        Instant armedAt = Instant.parse("2026-11-03T12:00:01.123456Z");
        Map<String, ArmedFaultRecord> samples = Map.of(
                "F1-P",
                new ArmedFaultRecord(
                        "F1-P",
                        "HEADER",
                        mapOf(
                                "faultProfileId",
                                "F1-P",
                                "trialId",
                                "t1",
                                "orderId",
                                "11111111-1111-1111-1111-111111111111",
                                "mechanism",
                                "HEADER",
                                "header",
                                "X-Thesis-Fault",
                                "code",
                                "CHARGE_FAIL",
                                "service",
                                "payment"),
                        armedAt),
                "F2",
                new ArmedFaultRecord(
                        "F2",
                        "HEADER",
                        mapOf(
                                "faultProfileId",
                                "F2",
                                "code",
                                "ARRANGE_REJECTED",
                                "service",
                                "shipping",
                                "header",
                                "X-Thesis-Fault"),
                        armedAt),
                "F3-Recovered",
                new ArmedFaultRecord(
                        "F3-Recovered",
                        "HEADER",
                        mapOf(
                                "faultProfileId",
                                "F3-Recovered",
                                "chargeCode",
                                "F3_CAPTURE_LOST_RESPONSE",
                                "opacityMode",
                                "header-presence",
                                "service",
                                "payment"),
                        armedAt),
                "F3-Unresolved",
                new ArmedFaultRecord(
                        "F3-Unresolved",
                        "HEADER",
                        mapOf(
                                "faultProfileId",
                                "F3-Unresolved",
                                "chargeCode",
                                "F3_CAPTURE_LOST_RESPONSE",
                                "resolveCode",
                                "RESOLVE_TRANSPORT_OPAQUE",
                                "opacityMode",
                                "header-presence",
                                "service",
                                "payment"),
                        armedAt));

        ExperimentDefinition exp = experiment("exp-f-profiles");
        FileResultStore store = new FileResultStore(tmp, exp);
        store.saveExperiment(exp);

        for (var e : samples.entrySet()) {
            String trialId = "trial-" + e.getKey();
            TrialRecord measured = completed(exp, trialId, e.getValue());
            store.saveTrial(measured);
            TrialRecord durable = store.readTrial(trialId);

            assertThat(durable.faultArmedRecord()).isEqualTo(e.getValue());
            assertThat(durable.faultArmedRecord().effectiveConfig())
                    .containsExactlyEntriesOf(e.getValue().effectiveConfig());
            assertThat(durable.faultArmedRecord().armedAt()).isEqualTo(armedAt);
            assertThat(durable).isEqualTo(measured);
            G5AcceptanceWriter.assertUnchanged(measured, durable);

            // Nested map values still present in canonical JSON text.
            String json = Files.readString(store.trialJsonPath(trialId));
            for (String v : e.getValue().effectiveConfig().values()) {
                assertThat(json).contains(v);
            }
            assertThat(json).contains("2026-11-03T12:00:01.123456Z");
        }
    }

    private static InputStream resource(String path) {
        return G5HistoricalTrialRecordCompatTest.class.getClassLoader().getResourceAsStream(path);
    }

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return Map.copyOf(m);
    }

    private static ExperimentDefinition experiment(String id) {
        return new ExperimentDefinition(
                id,
                "protocol-v0",
                "pre-pilot",
                List.of("A"),
                List.of("F0"),
                1,
                BusinessInputs.protocolPilotDefaults(),
                RunnerTiming.protocolDefaults(),
                "test-sha",
                Instant.parse("2026-11-03T12:00:00Z"));
    }

    private static TrialRecord completed(ExperimentDefinition exp, String trialId, ArmedFaultRecord armed) {
        UUID orderId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
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
                Instant.parse("2026-11-03T12:00:00Z"),
                Instant.parse("2026-11-03T12:00:01Z"),
                null,
                Instant.parse("2026-11-03T12:02:00Z"),
                Instant.parse("2026-11-03T12:02:30Z"),
                Instant.parse("2026-11-03T12:02:31Z"),
                TerminatedBy.T_RUN_MAX,
                true,
                "order-" + orderId,
                "run-1",
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
                        Instant.parse("2026-11-03T12:02:31Z")),
                null,
                armed,
                100,
                null);
    }
}
