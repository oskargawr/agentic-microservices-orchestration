package pl.edu.pw.thesis.experiment.runner.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.experiment.oracle.IndependentOracle;
import pl.edu.pw.thesis.experiment.oracle.OraclePattern;
import pl.edu.pw.thesis.experiment.oracle.OracleReasonCode;
import pl.edu.pw.thesis.experiment.oracle.OracleResult;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.oracle.snapshot.PaymentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ReservationView;
import pl.edu.pw.thesis.experiment.runner.config.JdbcAllowlist;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.fault.CompositeFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F3RecoveredFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.F3UnresolvedFaultController;
import pl.edu.pw.thesis.experiment.runner.fault.FaultArmRequest;
import pl.edu.pw.thesis.experiment.runner.fault.FaultController;
import pl.edu.pw.thesis.experiment.runner.fault.FaultProfileDescriptor;
import pl.edu.pw.thesis.experiment.runner.fault.FaultVerifyResult;
import pl.edu.pw.thesis.experiment.runner.fault.PaymentF3FaultInjectionVerifier;
import pl.edu.pw.thesis.experiment.runner.fault.PaymentInjectionCheckResult;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.reset.StateReset;
import pl.edu.pw.thesis.experiment.runner.store.FileResultStore;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;
import pl.edu.pw.thesis.experiment.runner.variant.ExecutionHandle;
import pl.edu.pw.thesis.experiment.runner.variant.VariantAdapter;
import pl.edu.pw.thesis.experiment.runner.variant.WorkflowStopInfo;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

/**
 * Runner lifecycle coverage for F3 profiles (mocked VariantAdapter + Oracle; no live Temporal).
 */
@ExtendWith(MockitoExtension.class)
class TrialRunnerF3LifecycleTest {

    @TempDir Path tmp;

    @Mock VariantAdapter variantAdapter;
    @Mock IndependentOracle oracle;
    @Mock StateReset stateReset;

    FakeClock clock;
    FileResultStore store;
    AtomicInteger recoveredChecks;
    AtomicInteger unresolvedChecks;
    DisarmTrackingFaultController disarmTracker;

    @BeforeEach
    void setUp() throws Exception {
        clock = new FakeClock(Instant.parse("2026-11-03T12:00:00Z"));
        recoveredChecks = new AtomicInteger();
        unresolvedChecks = new AtomicInteger();
        disarmTracker = null;
        lenient()
                .when(stateReset.resetToProtocolBaseline(anyString()))
                .thenReturn(new StateReset.BaselineSnapshot("SKU-MVP-1", 100, 0));
    }

    @Test
    void f3Recovered_armVerifyPersistsEffectiveConfig_andDisarms() throws Exception {
        ExperimentDefinition experiment = experimentWithProfiles(List.of("F3-Recovered"));
        store = new FileResultStore(tmp, experiment);
        store.saveExperiment(experiment);
        TrialRunner runner = newRunner(experiment, f3Verifier(true, true));

        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t-f3r", "F3-Recovered", 0, orderId,
                experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-1");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-03T12:00:10Z"), "SUCCESS"));
        when(oracle.evaluate(any())).thenReturn(oracleSuccess(orderId, "t-f3r"));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        ArmedFaultRecord persisted = (ArmedFaultRecord) record.faultArmedRecord();
        assertThat(persisted.effectiveConfig())
                .containsEntry("faultProfileId", "F3-Recovered")
                .containsEntry("chargeCode", "F3_CAPTURE_LOST_RESPONSE")
                .containsEntry("opacityMode", "header-presence")
                .doesNotContainKey("resolveCode");
        assertThat(recoveredChecks.get()).isEqualTo(1);
        ArgumentCaptor<ArmedFaultRecord> armedCaptor = ArgumentCaptor.forClass(ArmedFaultRecord.class);
        verify(variantAdapter).start(any(), armedCaptor.capture());
        assertThat(armedCaptor.getValue().effectiveConfig())
                .containsEntry("chargeCode", "F3_CAPTURE_LOST_RESPONSE");
        // TrialRunner finally: exactly one disarm when arm succeeded
        assertThat(disarmTracker.disarmCount.get()).isEqualTo(1);
        assertThat(disarmTracker.lastDisarmedTrialId).isEqualTo("t-f3r");
        assertThat(runner.isFurtherTrialsBlocked()).isFalse();
    }

    @Test
    void f3Unresolved_armVerifyGateAccepts_andRecordsResolveCode() throws Exception {
        ExperimentDefinition experiment = experimentWithProfiles(List.of("F3-Unresolved"));
        store = new FileResultStore(tmp, experiment);
        store.saveExperiment(experiment);
        TrialRunner runner = newRunner(experiment, f3Verifier(true, true));

        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t-f3u", "F3-Unresolved", 0, orderId,
                experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-u");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-03T12:00:10Z"), "AMBIGUOUS"));
        when(oracle.evaluate(any())).thenReturn(oracleAmbiguousCapturedHeld(orderId, "t-f3u"));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        ArmedFaultRecord persisted = (ArmedFaultRecord) record.faultArmedRecord();
        assertThat(persisted.effectiveConfig())
                .containsEntry("faultProfileId", "F3-Unresolved")
                .containsEntry("chargeCode", "F3_CAPTURE_LOST_RESPONSE")
                .containsEntry("resolveCode", "RESOLVE_TRANSPORT_OPAQUE")
                .containsEntry("opacityMode", "header-presence");
        assertThat(record.oracle().classification()).isEqualTo("AMBIGUOUS");
        assertThat(unresolvedChecks.get()).isEqualTo(1);
        assertThat(disarmTracker.disarmCount.get()).isEqualTo(1);
        assertThat(disarmTracker.lastDisarmedTrialId).isEqualTo("t-f3u");
    }

    @Test
    void f3Unresolved_gateRejects_whenOracleSnapshotHasCancelledShipment() throws Exception {
        ExperimentDefinition experiment = experimentWithProfiles(List.of("F3-Unresolved"));
        store = new FileResultStore(tmp, experiment);
        store.saveExperiment(experiment);
        TrialRunner runner = newRunner(experiment, f3Verifier(true, true));

        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t-bad-ship", "F3-Unresolved", 0, orderId,
                experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-bad");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-03T12:00:10Z"), "AMBIGUOUS"));
        when(oracle.evaluate(any())).thenReturn(oracleAmbiguousWithCancelledShipment(orderId, "t-bad-ship"));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.EXCLUDED);
        assertThat(record.exclusionReason()).isEqualTo("F3_UNRESOLVED_ACCEPTANCE_FAILED");
        assertThat(record.infraError()).contains("CANCELLED");
        // Exclusion after arm still runs finally disarm once
        assertThat(disarmTracker.disarmCount.get()).isEqualTo(1);
        assertThat(disarmTracker.lastDisarmedTrialId).isEqualTo("t-bad-ship");
    }

    @Test
    void f3Recovered_verifyFails_doesNotStartWorkflow() throws Exception {
        ExperimentDefinition experiment = experimentWithProfiles(List.of("F3-Recovered"));
        store = new FileResultStore(tmp, experiment);
        store.saveExperiment(experiment);
        TrialRunner runner = newRunner(experiment, f3Verifier(false, false));

        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t-nov", "F3-Recovered", 0, orderId,
                experiment.businessDefaults());

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.EXCLUDED);
        assertThat(record.exclusionReason()).isEqualTo("FAULT_VERIFY_FAILED");
        assertThat(record.infraError()).contains("PAYMENT_F3_RECOVERED_NOT_CONFIRMED");
        verify(variantAdapter, never()).start(any(), any());
        // Arm succeeded before verify fail → finally still disarms once
        assertThat(disarmTracker.disarmCount.get()).isEqualTo(1);
        assertThat(disarmTracker.lastDisarmedTrialId).isEqualTo("t-nov");
    }

    @Test
    void f3Recovered_disarmFailure_blocksFurtherTrials() throws Exception {
        ExperimentDefinition experiment = experimentWithProfiles(List.of("F3-Recovered"));
        store = new FileResultStore(tmp, experiment);
        store.saveExperiment(experiment);
        TrialRunner runner = newRunner(experiment, f3Verifier(true, true));
        disarmTracker.failDisarm = true;

        UUID orderId = UUID.randomUUID();
        TrialSpec spec = TrialSpec.forVariantA(
                experiment.experimentId(), "t-disarm-fail", "F3-Recovered", 0, orderId,
                experiment.businessDefaults());
        ExecutionHandle handle = new ExecutionHandle(spec.workflowId(), "run-df");
        when(variantAdapter.start(any(), any())).thenReturn(handle);
        when(variantAdapter.awaitStop(any(), any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-03T12:00:10Z"), "SUCCESS"));
        when(oracle.evaluate(any())).thenReturn(oracleSuccess(orderId, "t-disarm-fail"));

        TrialRecord record = runner.run(spec);

        assertThat(record.harnessStatus()).isEqualTo(HarnessStatus.COMPLETED);
        assertThat(disarmTracker.disarmCount.get()).isEqualTo(1);
        assertThat(runner.isFurtherTrialsBlocked()).isTrue();
    }

    private TrialRunner newRunner(ExperimentDefinition experiment, PaymentF3FaultInjectionVerifier verifier) {
        RunnerEnvironment environment = new RunnerEnvironment(
                "lab",
                "jdbc:postgresql://localhost:5433/business",
                "thesis",
                "thesis",
                JdbcAllowlist.of("jdbc:postgresql://localhost:5433/business"),
                tmp,
                new TemporalSettings("localhost:7233", "default"),
                experiment.timing());
        var map = new java.util.LinkedHashMap<String, FaultController>();
        map.put("F0", new pl.edu.pw.thesis.experiment.runner.fault.F0FaultController(clock));
        map.put(F3RecoveredFaultController.PROFILE, new F3RecoveredFaultController(clock, verifier));
        map.put(F3UnresolvedFaultController.PROFILE, new F3UnresolvedFaultController(clock, verifier));
        disarmTracker = new DisarmTrackingFaultController(new CompositeFaultController(map));
        return new TrialRunner(
                experiment,
                environment,
                stateReset,
                disarmTracker,
                variantAdapter,
                oracle,
                store,
                clock);
    }

    private PaymentF3FaultInjectionVerifier f3Verifier(boolean recoveredOk, boolean unresolvedOk) {
        return new PaymentF3FaultInjectionVerifier() {
            @Override
            public PaymentInjectionCheckResult checkRecovered() {
                recoveredChecks.incrementAndGet();
                return recoveredOk
                        ? PaymentInjectionCheckResult.enabled("ok")
                        : PaymentInjectionCheckResult.disabledOrUnknown("no");
            }

            @Override
            public PaymentInjectionCheckResult checkUnresolved() {
                unresolvedChecks.incrementAndGet();
                return unresolvedOk
                        ? PaymentInjectionCheckResult.enabled("ok")
                        : PaymentInjectionCheckResult.disabledOrUnknown("no");
            }
        };
    }

    private ExperimentDefinition experimentWithProfiles(List<String> profiles) {
        RunnerTiming timing = new RunnerTiming(
                Duration.ofSeconds(30), Duration.ofSeconds(120), Duration.ofSeconds(5));
        return new ExperimentDefinition(
                "exp-f3",
                "protocol-v0",
                "pilot",
                List.of("A"),
                profiles,
                1,
                BusinessInputs.protocolPilotDefaults(),
                timing,
                "sha",
                Instant.parse("2026-11-03T12:00:00Z"));
    }

    private static OracleResult oracleSuccess(UUID orderId, String trialId) {
        BusinessSnapshot snapshot = new BusinessSnapshot(
                orderId,
                "SKU-MVP-1",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
        return new OracleResult(
                BusinessOutcome.CONFIRMED_SUCCESS,
                OraclePattern.SUCCESS,
                OracleReasonCode.NONE,
                List.of(),
                List.of(),
                snapshot,
                "order-" + orderId,
                trialId,
                OracleResult.EVALUATOR_VERSION,
                Instant.parse("2026-11-03T12:00:40Z"));
    }

    private static OracleResult oracleAmbiguousCapturedHeld(UUID orderId, String trialId) {
        BusinessSnapshot snapshot = new BusinessSnapshot(
                orderId,
                "SKU-MVP-1",
                List.of(new ReservationView(
                        UUID.randomUUID(), orderId, "SKU-MVP-1", 1, ReservationState.HELD)),
                Optional.of(new PaymentView(
                        UUID.randomUUID(), orderId, 2599, "PLN", PaymentState.CAPTURED)),
                Optional.empty(),
                Optional.empty(),
                List.of());
        return new OracleResult(
                BusinessOutcome.AMBIGUOUS,
                OraclePattern.NONE,
                OracleReasonCode.COMPENSATION_INCOMPLETE,
                List.of(),
                List.of(),
                snapshot,
                "order-" + orderId,
                trialId,
                OracleResult.EVALUATOR_VERSION,
                Instant.parse("2026-11-03T12:00:40Z"));
    }

    private static OracleResult oracleAmbiguousWithCancelledShipment(UUID orderId, String trialId) {
        BusinessSnapshot snapshot = new BusinessSnapshot(
                orderId,
                "SKU-MVP-1",
                List.of(new ReservationView(
                        UUID.randomUUID(), orderId, "SKU-MVP-1", 1, ReservationState.HELD)),
                Optional.of(new PaymentView(
                        UUID.randomUUID(), orderId, 2599, "PLN", PaymentState.CAPTURED)),
                Optional.of(new pl.edu.pw.thesis.experiment.oracle.snapshot.ShipmentView(
                        UUID.randomUUID(),
                        orderId,
                        pl.edu.pw.thesis.contracts.shipping.ShipmentState.CANCELLED)),
                Optional.empty(),
                List.of());
        return new OracleResult(
                BusinessOutcome.AMBIGUOUS,
                OraclePattern.NONE,
                OracleReasonCode.COMPENSATION_INCOMPLETE,
                List.of(),
                List.of(),
                snapshot,
                "order-" + orderId,
                trialId,
                OracleResult.EVALUATOR_VERSION,
                Instant.parse("2026-11-03T12:00:40Z"));
    }

    /**
     * Observes TrialRunner's single {@code finally} disarm call without changing FaultController
     * semantics. Shared for F3-Recovered and F3-Unresolved (same lifecycle path).
     */
    static final class DisarmTrackingFaultController implements FaultController {
        private final FaultController delegate;
        final AtomicInteger disarmCount = new AtomicInteger();
        volatile String lastDisarmedTrialId;
        volatile boolean failDisarm;

        DisarmTrackingFaultController(FaultController delegate) {
            this.delegate = delegate;
        }

        @Override
        public ArmedFaultRecord arm(FaultArmRequest request) {
            return delegate.arm(request);
        }

        @Override
        public FaultVerifyResult verify(ArmedFaultRecord armed, TrialSpec trial) {
            return delegate.verify(armed, trial);
        }

        @Override
        public void disarm(String trialId) {
            disarmCount.incrementAndGet();
            lastDisarmedTrialId = trialId;
            if (failDisarm) {
                throw new IllegalStateException("controlled disarm failure");
            }
            delegate.disarm(trialId);
        }

        @Override
        public FaultProfileDescriptor describe(String faultProfileId) {
            return delegate.describe(faultProfileId);
        }
    }

    static final class FakeClock implements RunnerClock {
        private Instant now;

        FakeClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant now() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            now = now.plus(duration);
        }
    }
}
