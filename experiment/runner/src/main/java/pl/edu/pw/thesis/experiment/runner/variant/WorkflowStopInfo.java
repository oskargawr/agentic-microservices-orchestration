package pl.edu.pw.thesis.experiment.runner.variant;

import java.time.Instant;

public record WorkflowStopInfo(
        String temporalStatus,
        boolean terminal,
        Instant observedAt,
        String workflowOutcome) {}
