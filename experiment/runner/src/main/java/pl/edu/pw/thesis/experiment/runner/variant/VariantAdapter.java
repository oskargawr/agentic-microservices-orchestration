package pl.edu.pw.thesis.experiment.runner.variant;

import java.time.Instant;
import java.util.Optional;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;

public interface VariantAdapter {

    ExecutionHandle start(TrialSpec trial, ArmedFaultRecord armed);

    WorkflowStopInfo describe(ExecutionHandle handle);

    /** Poll until terminal or {@code deadlineExclusive} reached. */
    WorkflowStopInfo awaitStop(ExecutionHandle handle, Instant deadlineExclusive);

    /** Temporal workflow terminate (R6) — not cancel; not business compensation. */
    void terminate(ExecutionHandle handle, String reason);

    Optional<String> readWorkflowOutcome(ExecutionHandle handle);
}
