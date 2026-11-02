package pl.edu.pw.thesis.experiment.runner.fault;

import java.time.Instant;
import java.util.Map;

public record ArmedFaultRecord(
        String faultProfileId,
        String mechanism,
        Map<String, String> effectiveConfig,
        Instant armedAt) {}
