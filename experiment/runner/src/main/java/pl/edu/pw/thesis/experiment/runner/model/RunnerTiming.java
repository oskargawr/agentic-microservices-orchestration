package pl.edu.pw.thesis.experiment.runner.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Harness timing. {@code tObserve}/{@code tRunMax} are protocol constants (D-012).
 * {@code quiesceWait} is configurable (D-R13), not a protocol constant.
 */
public record RunnerTiming(Duration tObserve, Duration tRunMax, Duration quiesceWait) {

    public static final Duration PROTOCOL_T_OBSERVE = Duration.ofSeconds(30);
    public static final Duration PROTOCOL_T_RUN_MAX = Duration.ofSeconds(120);
    public static final Duration DEFAULT_QUIESCE_WAIT = Duration.ofMillis(5000);

    public RunnerTiming {
        Objects.requireNonNull(tObserve, "tObserve");
        Objects.requireNonNull(tRunMax, "tRunMax");
        Objects.requireNonNull(quiesceWait, "quiesceWait");
        if (tObserve.isNegative() || tRunMax.isNegative() || quiesceWait.isNegative()) {
            throw new IllegalArgumentException("timings must be non-negative");
        }
    }

    public static RunnerTiming protocolDefaults() {
        return new RunnerTiming(PROTOCOL_T_OBSERVE, PROTOCOL_T_RUN_MAX, DEFAULT_QUIESCE_WAIT);
    }
}
