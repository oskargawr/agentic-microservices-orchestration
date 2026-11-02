package pl.edu.pw.thesis.experiment.runner.fault;

import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;

public interface FaultController {

    ArmedFaultRecord arm(FaultArmRequest request);

    FaultVerifyResult verify(ArmedFaultRecord armed, TrialSpec trial);

    void disarm(String trialId);

    FaultProfileDescriptor describe(String faultProfileId);
}
