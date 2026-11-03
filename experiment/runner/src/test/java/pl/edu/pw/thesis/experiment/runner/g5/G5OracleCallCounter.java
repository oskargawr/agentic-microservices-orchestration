package pl.edu.pw.thesis.experiment.runner.g5;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import pl.edu.pw.thesis.experiment.oracle.IndependentOracle;
import pl.edu.pw.thesis.experiment.oracle.OracleEvaluationRequest;

/**
 * Test-only spy around a real {@link IndependentOracle} for TrialRunner-path evidence.
 *
 * <p>Uses Mockito {@code spy} (final class supported by the project's mockito mock maker) so the
 * production evaluate path still runs; counts {@link IndependentOracle#evaluate(OracleEvaluationRequest)}
 * invocations only.
 */
public final class G5OracleCallCounter {

    private final IndependentOracle spy;
    private final AtomicInteger evaluateCalls = new AtomicInteger();
    private final List<OracleEvaluationRequest> requests =
            Collections.synchronizedList(new ArrayList<>());

    private G5OracleCallCounter(IndependentOracle spy) {
        this.spy = spy;
    }

    public static G5OracleCallCounter wrap(IndependentOracle real) {
        Objects.requireNonNull(real, "real");
        IndependentOracle spyOracle = spy(real);
        G5OracleCallCounter counter = new G5OracleCallCounter(spyOracle);
        try {
            doAnswer(invocation -> {
                        OracleEvaluationRequest req = invocation.getArgument(0);
                        counter.evaluateCalls.incrementAndGet();
                        counter.requests.add(req);
                        return invocation.callRealMethod();
                    })
                    .when(spyOracle)
                    .evaluate(any(OracleEvaluationRequest.class));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instrument IndependentOracle spy", e);
        }
        return counter;
    }

    /** Spy instance to inject into {@code TrialRunner}. */
    public IndependentOracle oracle() {
        return spy;
    }

    public int evaluateCalls() {
        return evaluateCalls.get();
    }

    public List<OracleEvaluationRequest> requests() {
        return List.copyOf(requests);
    }

    public void assertExactlyOnce() {
        if (evaluateCalls.get() != 1) {
            throw new AssertionError(
                    "Expected IndependentOracle.evaluate exactly once via TrialRunner, got "
                            + evaluateCalls.get());
        }
    }
}
