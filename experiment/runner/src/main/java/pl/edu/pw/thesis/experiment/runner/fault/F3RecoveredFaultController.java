package pl.edu.pw.thesis.experiment.runner.fault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

/**
 * F3-Recovered: charge {@code F3_CAPTURE_LOST_RESPONSE}; resolve/GET without transport opacity.
 */
public final class F3RecoveredFaultController implements FaultController {

    public static final String PROFILE = "F3-Recovered";
    public static final String MECHANISM_HEADER = "HEADER";
    public static final String HEADER_NAME = "X-Thesis-Fault";
    public static final String CHARGE_CODE = "F3_CAPTURE_LOST_RESPONSE";
    public static final String OPACITY_MODE = "header-presence";
    public static final String SERVICE_PAYMENT = "payment";

    private final RunnerClock clock;
    private final PaymentF3FaultInjectionVerifier injectionVerifier;
    private final ConcurrentHashMap<String, ArmedFaultRecord> armed = new ConcurrentHashMap<>();

    public F3RecoveredFaultController(
            RunnerClock clock, PaymentF3FaultInjectionVerifier injectionVerifier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.injectionVerifier = Objects.requireNonNull(injectionVerifier, "injectionVerifier");
    }

    @Override
    public ArmedFaultRecord arm(FaultArmRequest request) {
        if (!PROFILE.equals(request.faultProfileId())) {
            throw new IllegalArgumentException(
                    "F3RecoveredFaultController only supports F3-Recovered, got "
                            + request.faultProfileId());
        }
        Map<String, String> effective = baseConfig(request);
        ArmedFaultRecord record =
                new ArmedFaultRecord(PROFILE, MECHANISM_HEADER, Map.copyOf(effective), clock.now());
        armed.put(request.trialId(), record);
        return record;
    }

    @Override
    public FaultVerifyResult verify(ArmedFaultRecord armedRecord, TrialSpec trial) {
        FaultVerifyResult shape = verifyShape(armedRecord, trial);
        if (!shape.ok()) {
            return shape;
        }
        PaymentInjectionCheckResult injection = injectionVerifier.checkRecovered();
        if (!injection.enabled()) {
            return FaultVerifyResult.failed(
                    "PAYMENT_F3_RECOVERED_NOT_CONFIRMED: " + injection.detail());
        }
        return FaultVerifyResult.success();
    }

    @Override
    public void disarm(String trialId) {
        armed.remove(trialId);
    }

    @Override
    public FaultProfileDescriptor describe(String faultProfileId) {
        if (!PROFILE.equals(faultProfileId)) {
            throw new IllegalArgumentException("unknown profile " + faultProfileId);
        }
        return new FaultProfileDescriptor(
                PROFILE,
                MECHANISM_HEADER,
                "F3 charge lost-response then normal resolve/GET expose CAPTURED");
    }

    static Map<String, String> baseConfig(FaultArmRequest request) {
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("faultProfileId", PROFILE);
        effective.put("trialId", request.trialId());
        effective.put("orderId", request.orderId().toString());
        effective.put("mechanism", MECHANISM_HEADER);
        effective.put("header", HEADER_NAME);
        effective.put("chargeCode", CHARGE_CODE);
        effective.put("opacityMode", OPACITY_MODE);
        effective.put("service", SERVICE_PAYMENT);
        return effective;
    }

    private FaultVerifyResult verifyShape(ArmedFaultRecord armedRecord, TrialSpec trial) {
        if (armedRecord == null) {
            return FaultVerifyResult.failed("missing armed record");
        }
        if (!PROFILE.equals(armedRecord.faultProfileId())) {
            return FaultVerifyResult.failed("expected F3-Recovered");
        }
        if (!MECHANISM_HEADER.equals(armedRecord.mechanism())) {
            return FaultVerifyResult.failed("F3-Recovered mechanism must be HEADER");
        }
        Map<String, String> cfg = armedRecord.effectiveConfig();
        if (!CHARGE_CODE.equals(cfg.get("chargeCode"))) {
            return FaultVerifyResult.failed("chargeCode must be F3_CAPTURE_LOST_RESPONSE");
        }
        if (!HEADER_NAME.equals(cfg.get("header"))) {
            return FaultVerifyResult.failed("header must be X-Thesis-Fault");
        }
        if (!trial.trialId().equals(cfg.get("trialId"))) {
            return FaultVerifyResult.failed("effectiveConfig.trialId mismatch");
        }
        if (!trial.orderId().toString().equals(cfg.get("orderId"))) {
            return FaultVerifyResult.failed("effectiveConfig.orderId mismatch");
        }
        if (!PROFILE.equals(cfg.get("faultProfileId"))) {
            return FaultVerifyResult.failed("effectiveConfig.faultProfileId mismatch");
        }
        return FaultVerifyResult.success();
    }
}
