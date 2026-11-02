package pl.edu.pw.thesis.experiment.runner.fault;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

/** Phase-1 F0 no-op fault profile — no business/transport injection. */
public final class F0FaultController implements FaultController {

    public static final String PROFILE_F0 = "F0";

    private final RunnerClock clock;
    private final ConcurrentHashMap<String, ArmedFaultRecord> armed = new ConcurrentHashMap<>();

    public F0FaultController(RunnerClock clock) {
        this.clock = clock;
    }

    @Override
    public ArmedFaultRecord arm(FaultArmRequest request) {
        if (!PROFILE_F0.equals(request.faultProfileId())) {
            throw new IllegalArgumentException(
                    "F0FaultController only supports F0, got " + request.faultProfileId());
        }
        ArmedFaultRecord record = new ArmedFaultRecord(
                PROFILE_F0,
                "NONE",
                Map.of("injection", "none"),
                clock.now());
        armed.put(request.trialId(), record);
        return record;
    }

    @Override
    public FaultVerifyResult verify(ArmedFaultRecord armedRecord, TrialSpec trial) {
        if (armedRecord == null) {
            return FaultVerifyResult.failed("missing armed record");
        }
        if (!PROFILE_F0.equals(armedRecord.faultProfileId())) {
            return FaultVerifyResult.failed("expected F0");
        }
        if (!"NONE".equals(armedRecord.mechanism())) {
            return FaultVerifyResult.failed("F0 mechanism must be NONE");
        }
        return FaultVerifyResult.success();
    }

    @Override
    public void disarm(String trialId) {
        armed.remove(trialId);
    }

    @Override
    public FaultProfileDescriptor describe(String faultProfileId) {
        if (!PROFILE_F0.equals(faultProfileId)) {
            throw new IllegalArgumentException("unknown profile " + faultProfileId);
        }
        return new FaultProfileDescriptor(
                PROFILE_F0, "NONE", "No-op fault profile — no injection");
    }
}
