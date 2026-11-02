package pl.edu.pw.thesis.experiment.runner.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ExperimentDefinition(
        String experimentId,
        String protocolId,
        String phase,
        List<String> variants,
        List<String> faultProfileIds,
        int n,
        BusinessInputs businessDefaults,
        RunnerTiming timing,
        String gitSha,
        Instant createdAt) {

    public ExperimentDefinition {
        Objects.requireNonNull(experimentId, "experimentId");
        Objects.requireNonNull(protocolId, "protocolId");
        Objects.requireNonNull(phase, "phase");
        variants = List.copyOf(variants);
        faultProfileIds = List.copyOf(faultProfileIds);
        Objects.requireNonNull(businessDefaults, "businessDefaults");
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(createdAt, "createdAt");
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
    }
}
