package pl.edu.pw.thesis.experiment.runner.g5;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.variant.ExecutionHandle;
import pl.edu.pw.thesis.experiment.runner.variant.VariantAdapter;
import pl.edu.pw.thesis.experiment.runner.variant.WorkflowStopInfo;

/** Test-only spy: counts measured {@code terminate} calls; never exposes a cancel API. */
public final class CountingVariantAdapter implements VariantAdapter {

    private final VariantAdapter delegate;
    private final AtomicInteger terminateCalls = new AtomicInteger();
    private final AtomicInteger startCalls = new AtomicInteger();

    public CountingVariantAdapter(VariantAdapter delegate) {
        this.delegate = delegate;
    }

    public int terminateCalls() {
        return terminateCalls.get();
    }

    public int startCalls() {
        return startCalls.get();
    }

    @Override
    public ExecutionHandle start(TrialSpec trial, ArmedFaultRecord armed) {
        startCalls.incrementAndGet();
        return delegate.start(trial, armed);
    }

    @Override
    public WorkflowStopInfo describe(ExecutionHandle handle) {
        return delegate.describe(handle);
    }

    @Override
    public WorkflowStopInfo awaitStop(ExecutionHandle handle, Instant deadlineExclusive) {
        return delegate.awaitStop(handle, deadlineExclusive);
    }

    @Override
    public void terminate(ExecutionHandle handle, String reason) {
        terminateCalls.incrementAndGet();
        delegate.terminate(handle, reason);
    }

    @Override
    public Optional<String> readWorkflowOutcome(ExecutionHandle handle) {
        return delegate.readWorkflowOutcome(handle);
    }
}
