package pl.edu.pw.thesis.experiment.runner.model;

import java.util.Objects;
import java.util.UUID;

public record TrialSpec(
        String experimentId,
        String trialId,
        String variant,
        String faultProfileId,
        int repetitionIndex,
        UUID orderId,
        BusinessInputs businessInputs,
        String workflowId) {

    public TrialSpec {
        Objects.requireNonNull(experimentId, "experimentId");
        Objects.requireNonNull(trialId, "trialId");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(faultProfileId, "faultProfileId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(businessInputs, "businessInputs");
        Objects.requireNonNull(workflowId, "workflowId");
    }

    public static TrialSpec forVariantA(
            String experimentId, String trialId, String faultProfileId, int repetitionIndex,
            UUID orderId, BusinessInputs inputs) {
        return new TrialSpec(
                experimentId,
                trialId,
                "A",
                faultProfileId,
                repetitionIndex,
                orderId,
                inputs,
                "order-" + orderId);
    }
}
