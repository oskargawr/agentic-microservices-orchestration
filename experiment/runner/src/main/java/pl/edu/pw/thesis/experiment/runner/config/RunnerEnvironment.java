package pl.edu.pw.thesis.experiment.runner.config;

import java.nio.file.Path;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

public record RunnerEnvironment(
        String envId,
        String jdbcUrl,
        String jdbcUser,
        String jdbcPassword,
        JdbcAllowlist allowlist,
        Path resultsRoot,
        TemporalSettings temporal,
        RunnerTiming timing) {

    public RunnerEnvironment {
        Objects.requireNonNull(envId, "envId");
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        Objects.requireNonNull(jdbcUser, "jdbcUser");
        Objects.requireNonNull(jdbcPassword, "jdbcPassword");
        Objects.requireNonNull(allowlist, "allowlist");
        Objects.requireNonNull(resultsRoot, "resultsRoot");
        Objects.requireNonNull(temporal, "temporal");
        Objects.requireNonNull(timing, "timing");
        if (envId.isBlank()) {
            throw new IllegalArgumentException("envId must not be blank");
        }
    }

    /** Validates allowlist before any destructive use. */
    public void assertSafeForDestructiveOps() {
        allowlist.requireAllowed(jdbcUrl);
    }
}
