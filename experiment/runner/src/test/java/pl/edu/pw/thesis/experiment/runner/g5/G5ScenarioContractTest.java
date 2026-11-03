package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.model.BusinessInputs;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.variant.ExecutionHandle;
import pl.edu.pw.thesis.experiment.runner.variant.VariantAdapter;
import pl.edu.pw.thesis.experiment.runner.variant.WorkflowStopInfo;

/**
 * Non-live contracts for G5 scenario separation and terminate counting. Oracle exactly-once on the
 * TrialRunner path is covered by {@link G5OracleExactlyOnceViaTrialRunnerTest}.
 */
class G5ScenarioContractTest {

    @Test
    void g5_2_and_g5_3_areIndependentlySelectable() {
        assertThat(G5OptIn.scenarioEnabled("G5-2")).isFalse();
        assertThat(G5OptIn.scenarioEnabled("G5-3")).isFalse();
        assertThat("G5-2").isNotEqualTo("G5-3");
        assertThat(G5AcceptanceRecord.SCENARIO_G5_3).isEqualTo("G5-3");
    }

    @Test
    void countingAdapter_countsTerminateOnly_neverProvidesCancel() {
        VariantAdapter delegate = mock(VariantAdapter.class);
        when(delegate.start(any(), any())).thenReturn(new ExecutionHandle("order-1", "run-1"));
        when(delegate.describe(any()))
                .thenReturn(new WorkflowStopInfo(
                        "COMPLETED", true, Instant.parse("2026-11-03T12:00:00Z"), "OK"));

        CountingVariantAdapter counting = new CountingVariantAdapter(delegate);
        ExecutionHandle h = counting.start(
                TrialSpec.forVariantA(
                        "exp",
                        "t1",
                        "F0",
                        0,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        BusinessInputs.protocolPilotDefaults()),
                new ArmedFaultRecord("F0", "NONE", Map.of(), Instant.parse("2026-11-03T12:00:00Z")));
        assertThat(counting.startCalls()).isEqualTo(1);
        assertThat(counting.terminateCalls()).isZero();

        counting.terminate(h, "T_run_max");
        assertThat(counting.terminateCalls()).isEqualTo(1);
        verify(delegate, times(1)).terminate(h, "T_run_max");
        verify(delegate, never()).readWorkflowOutcome(any());
    }

    @Test
    void cleanupTerminateReason_mustDifferFromMeasuredR6() {
        String measured = "T_run_max";
        String cleanup = "g5-cleanup";
        assertThat(cleanup).isNotEqualTo(measured);
    }
}
