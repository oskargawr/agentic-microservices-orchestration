package pl.edu.pw.thesis.experiment.runner.model;

import java.time.Instant;
import java.util.UUID;
import pl.edu.pw.thesis.experiment.runner.fault.ArmedFaultRecord;

/** Durable trial artifact (design §4.1 buckets, phase-1 subset). */
public record TrialRecord(
        String schemaVersion,
        String experimentId,
        String trialId,
        String protocolId,
        String phase,
        String variant,
        String faultProfileId,
        int repetitionIndex,
        UUID orderId,
        BusinessInputs businessInputs,
        String gitSha,
        Instant t0,
        Instant faultTriggeredAt,
        Instant faultObservedAt,
        Instant tWorkflowStop,
        Instant tTerminal,
        Instant tOracle,
        TerminatedBy terminatedBy,
        boolean workflowQuiesced,
        String workflowId,
        String runId,
        String temporalStatus,
        HarnessStatus harnessStatus,
        boolean includeInRqRates,
        ExclusionCategory exclusionCategory,
        String exclusionReason,
        OracleSummary oracle,
        String workflowOutcome,
        /** Typed so Jackson round-trip preserves {@link #equals} for G5 durable equality. */
        ArmedFaultRecord faultArmedRecord,
        Integer baselineOnHand,
        String infraError) {

    public static final String SCHEMA_VERSION = "th-402-phase1-1";

    public static TrialRecord excluded(
            TrialSpec spec,
            ExperimentDefinition experiment,
            Instant t0,
            String exclusionReason,
            ExclusionCategory category,
            String infraError) {
        return new TrialRecord(
                SCHEMA_VERSION,
                spec.experimentId(),
                spec.trialId(),
                experiment.protocolId(),
                experiment.phase(),
                spec.variant(),
                spec.faultProfileId(),
                spec.repetitionIndex(),
                spec.orderId(),
                spec.businessInputs(),
                experiment.gitSha(),
                t0,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                spec.workflowId(),
                null,
                null,
                HarnessStatus.EXCLUDED,
                false,
                category,
                exclusionReason,
                null,
                null,
                null,
                null,
                infraError);
    }
}
