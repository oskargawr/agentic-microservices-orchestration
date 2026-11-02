package pl.edu.pw.thesis.experiment.runner.fault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

/**
 * F1-P: arm existing payment {@code X-Thesis-Fault: CHARGE_FAIL} after inventory HELD (D-R5).
 *
 * <p>Verify is fail-closed: requires {@link PaymentFaultInjectionVerifier} confirmation that the
 * live Payment process honors the fault header (not a docs-only or HTTP-2xx-only check).
 */
public final class F1PFaultController implements FaultController {

    public static final String PROFILE_F1_P = "F1-P";
    public static final String MECHANISM_HEADER = "HEADER";
    public static final String HEADER_NAME = "X-Thesis-Fault";
    public static final String CODE_CHARGE_FAIL = "CHARGE_FAIL";
    public static final String SERVICE_PAYMENT = "payment";

    private final RunnerClock clock;
    private final PaymentFaultInjectionVerifier injectionVerifier;
    private final ConcurrentHashMap<String, ArmedFaultRecord> armed = new ConcurrentHashMap<>();

    public F1PFaultController(RunnerClock clock, PaymentFaultInjectionVerifier injectionVerifier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.injectionVerifier = Objects.requireNonNull(injectionVerifier, "injectionVerifier");
    }

    @Override
    public ArmedFaultRecord arm(FaultArmRequest request) {
        if (!PROFILE_F1_P.equals(request.faultProfileId())) {
            throw new IllegalArgumentException(
                    "F1PFaultController only supports F1-P, got " + request.faultProfileId());
        }
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("faultProfileId", PROFILE_F1_P);
        effective.put("trialId", request.trialId());
        effective.put("orderId", request.orderId().toString());
        effective.put("mechanism", MECHANISM_HEADER);
        effective.put("header", HEADER_NAME);
        effective.put("code", CODE_CHARGE_FAIL);
        effective.put("service", SERVICE_PAYMENT);

        ArmedFaultRecord record = new ArmedFaultRecord(
                PROFILE_F1_P, MECHANISM_HEADER, Map.copyOf(effective), clock.now());
        armed.put(request.trialId(), record);
        return record;
    }

    @Override
    public FaultVerifyResult verify(ArmedFaultRecord armedRecord, TrialSpec trial) {
        if (armedRecord == null) {
            return FaultVerifyResult.failed("missing armed record");
        }
        if (!PROFILE_F1_P.equals(armedRecord.faultProfileId())) {
            return FaultVerifyResult.failed("expected F1-P");
        }
        if (!MECHANISM_HEADER.equals(armedRecord.mechanism())) {
            return FaultVerifyResult.failed("F1-P mechanism must be HEADER");
        }
        Map<String, String> cfg = armedRecord.effectiveConfig();
        if (!CODE_CHARGE_FAIL.equals(cfg.get("code"))) {
            return FaultVerifyResult.failed("F1-P effectiveConfig.code must be CHARGE_FAIL");
        }
        if (!HEADER_NAME.equals(cfg.get("header"))) {
            return FaultVerifyResult.failed("F1-P effectiveConfig.header must be X-Thesis-Fault");
        }
        if (!trial.trialId().equals(cfg.get("trialId"))) {
            return FaultVerifyResult.failed("effectiveConfig.trialId mismatch");
        }
        if (!trial.orderId().toString().equals(cfg.get("orderId"))) {
            return FaultVerifyResult.failed("effectiveConfig.orderId mismatch");
        }
        if (!PROFILE_F1_P.equals(cfg.get("faultProfileId"))) {
            return FaultVerifyResult.failed("effectiveConfig.faultProfileId mismatch");
        }

        PaymentInjectionCheckResult injection = injectionVerifier.check();
        if (!injection.enabled()) {
            return FaultVerifyResult.failed(
                    "PAYMENT_FAULT_INJECTION_NOT_CONFIRMED: " + injection.detail());
        }
        return FaultVerifyResult.success();
    }

    @Override
    public void disarm(String trialId) {
        armed.remove(trialId);
    }

    @Override
    public FaultProfileDescriptor describe(String faultProfileId) {
        if (!PROFILE_F1_P.equals(faultProfileId)) {
            throw new IllegalArgumentException("unknown profile " + faultProfileId);
        }
        return new FaultProfileDescriptor(
                PROFILE_F1_P,
                MECHANISM_HEADER,
                "Charge fail after HELD via X-Thesis-Fault: CHARGE_FAIL");
    }
}
