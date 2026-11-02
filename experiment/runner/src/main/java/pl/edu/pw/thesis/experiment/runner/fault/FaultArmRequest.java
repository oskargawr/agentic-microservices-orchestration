package pl.edu.pw.thesis.experiment.runner.fault;

import java.util.UUID;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;

public record FaultArmRequest(
        String trialId, String faultProfileId, UUID orderId, String variant) {

    public static FaultArmRequest from(TrialSpec trial) {
        return new FaultArmRequest(
                trial.trialId(), trial.faultProfileId(), trial.orderId(), trial.variant());
    }
}
