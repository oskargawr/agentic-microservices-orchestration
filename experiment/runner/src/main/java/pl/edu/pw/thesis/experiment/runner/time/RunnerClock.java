package pl.edu.pw.thesis.experiment.runner.time;

import java.time.Duration;
import java.time.Instant;

/** Injectable clock for production waits and deterministic tests. */
public interface RunnerClock {

    Instant now();

    void sleep(Duration duration) throws InterruptedException;

    static RunnerClock system() {
        return new RunnerClock() {
            @Override
            public Instant now() {
                return Instant.now();
            }

            @Override
            public void sleep(Duration duration) throws InterruptedException {
                if (!duration.isZero() && !duration.isNegative()) {
                    Thread.sleep(duration.toMillis());
                }
            }
        };
    }
}
