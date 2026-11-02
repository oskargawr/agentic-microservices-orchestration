package pl.edu.pw.thesis.experiment.runner.fault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

/**
 * F2: arm shipping {@code X-Thesis-Fault: ARRANGE_REJECTED} after payment CAPTURED (OQ-F2-1).
 *
 * <p>Verify is fail-closed via {@link ShippingFaultInjectionVerifier}.
 */
public final class F2FaultController implements FaultController {

    public static final String PROFILE_F2 = "F2";
    public static final String MECHANISM_HEADER = "HEADER";
    public static final String HEADER_NAME = "X-Thesis-Fault";
    public static final String CODE_ARRANGE_REJECTED = "ARRANGE_REJECTED";
    public static final String SERVICE_SHIPPING = "shipping";

    private final RunnerClock clock;
    private final ShippingFaultInjectionVerifier injectionVerifier;
    private final ConcurrentHashMap<String, ArmedFaultRecord> armed = new ConcurrentHashMap<>();

    public F2FaultController(RunnerClock clock, ShippingFaultInjectionVerifier injectionVerifier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.injectionVerifier = Objects.requireNonNull(injectionVerifier, "injectionVerifier");
    }

    @Override
    public ArmedFaultRecord arm(FaultArmRequest request) {
        if (!PROFILE_F2.equals(request.faultProfileId())) {
            throw new IllegalArgumentException(
                    "F2FaultController only supports F2, got " + request.faultProfileId());
        }
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("faultProfileId", PROFILE_F2);
        effective.put("trialId", request.trialId());
        effective.put("orderId", request.orderId().toString());
        effective.put("mechanism", MECHANISM_HEADER);
        effective.put("header", HEADER_NAME);
        effective.put("code", CODE_ARRANGE_REJECTED);
        effective.put("service", SERVICE_SHIPPING);

        ArmedFaultRecord record =
                new ArmedFaultRecord(PROFILE_F2, MECHANISM_HEADER, Map.copyOf(effective), clock.now());
        armed.put(request.trialId(), record);
        return record;
    }

    @Override
    public FaultVerifyResult verify(ArmedFaultRecord armedRecord, TrialSpec trial) {
        if (armedRecord == null) {
            return FaultVerifyResult.failed("missing armed record");
        }
        if (!PROFILE_F2.equals(armedRecord.faultProfileId())) {
            return FaultVerifyResult.failed("expected F2");
        }
        if (!MECHANISM_HEADER.equals(armedRecord.mechanism())) {
            return FaultVerifyResult.failed("F2 mechanism must be HEADER");
        }
        Map<String, String> cfg = armedRecord.effectiveConfig();
        if (!CODE_ARRANGE_REJECTED.equals(cfg.get("code"))) {
            return FaultVerifyResult.failed("F2 effectiveConfig.code must be ARRANGE_REJECTED");
        }
        if (!HEADER_NAME.equals(cfg.get("header"))) {
            return FaultVerifyResult.failed("F2 effectiveConfig.header must be X-Thesis-Fault");
        }
        if (!trial.trialId().equals(cfg.get("trialId"))) {
            return FaultVerifyResult.failed("effectiveConfig.trialId mismatch");
        }
        if (!trial.orderId().toString().equals(cfg.get("orderId"))) {
            return FaultVerifyResult.failed("effectiveConfig.orderId mismatch");
        }
        if (!PROFILE_F2.equals(cfg.get("faultProfileId"))) {
            return FaultVerifyResult.failed("effectiveConfig.faultProfileId mismatch");
        }

        PaymentInjectionCheckResult injection = injectionVerifier.check();
        if (!injection.enabled()) {
            return FaultVerifyResult.failed(
                    "SHIPPING_FAULT_INJECTION_NOT_CONFIRMED: " + injection.detail());
        }
        return FaultVerifyResult.success();
    }

    @Override
    public void disarm(String trialId) {
        armed.remove(trialId);
    }

    @Override
    public FaultProfileDescriptor describe(String faultProfileId) {
        if (!PROFILE_F2.equals(faultProfileId)) {
            throw new IllegalArgumentException("unknown profile " + faultProfileId);
        }
        return new FaultProfileDescriptor(
                PROFILE_F2,
                MECHANISM_HEADER,
                "Arrange reject after CAPTURED via X-Thesis-Fault: ARRANGE_REJECTED");
    }
}
