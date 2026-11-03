package pl.edu.pw.thesis.experiment.runner.fault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

/** Routes arm/verify/disarm/describe by canonical fault profile id (F0, F1-P, F2, …). */
public final class CompositeFaultController implements FaultController {

    private final Map<String, FaultController> byProfile;

    public CompositeFaultController(Map<String, FaultController> byProfile) {
        Objects.requireNonNull(byProfile, "byProfile");
        if (byProfile.isEmpty()) {
            throw new IllegalArgumentException("byProfile must not be empty");
        }
        // Preserve insertion order (Map.copyOf does not guarantee LinkedHashMap order).
        this.byProfile = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byProfile));
    }

    /** F0 (no injection verifier) + F1-P (requires live payment injection confirmation). */
    public static CompositeFaultController f0AndF1P(
            RunnerClock clock, PaymentFaultInjectionVerifier f1pInjectionVerifier) {
        Map<String, FaultController> map = new LinkedHashMap<>();
        map.put(F0FaultController.PROFILE_F0, new F0FaultController(clock));
        map.put(F1PFaultController.PROFILE_F1_P, new F1PFaultController(clock, f1pInjectionVerifier));
        return new CompositeFaultController(map);
    }

    /** F0 + F2 (requires live shipping injection confirmation). */
    public static CompositeFaultController f0AndF2(
            RunnerClock clock, ShippingFaultInjectionVerifier f2InjectionVerifier) {
        Map<String, FaultController> map = new LinkedHashMap<>();
        map.put(F0FaultController.PROFILE_F0, new F0FaultController(clock));
        map.put(F2FaultController.PROFILE_F2, new F2FaultController(clock, f2InjectionVerifier));
        return new CompositeFaultController(map);
    }

    /** F0 + F3-Recovered (requires live payment F3 recovered preflight). */
    public static CompositeFaultController f0AndF3Recovered(
            RunnerClock clock, PaymentF3FaultInjectionVerifier f3Verifier) {
        Map<String, FaultController> map = new LinkedHashMap<>();
        map.put(F0FaultController.PROFILE_F0, new F0FaultController(clock));
        map.put(F3RecoveredFaultController.PROFILE, new F3RecoveredFaultController(clock, f3Verifier));
        return new CompositeFaultController(map);
    }

    /** F0 + F3-Unresolved (requires live payment F3 unresolved opacity preflight). */
    public static CompositeFaultController f0AndF3Unresolved(
            RunnerClock clock, PaymentF3FaultInjectionVerifier f3Verifier) {
        Map<String, FaultController> map = new LinkedHashMap<>();
        map.put(F0FaultController.PROFILE_F0, new F0FaultController(clock));
        map.put(
                F3UnresolvedFaultController.PROFILE,
                new F3UnresolvedFaultController(clock, f3Verifier));
        return new CompositeFaultController(map);
    }

    /** F0-only (phase-1 / tests that never arm F1-P / F2 / F3). */
    public static CompositeFaultController f0Only(RunnerClock clock) {
        return new CompositeFaultController(
                Map.of(F0FaultController.PROFILE_F0, new F0FaultController(clock)));
    }

    /**
     * S4-A: registers F0 + F1-P + F2 + F3-Recovered + F3-Unresolved concurrently.
     * Scheduling which profiles run is owned by {@code ExperimentDefinition.faultProfileIds()}.
     */
    public static CompositeFaultController variantAAllProfiles(
            RunnerClock clock,
            PaymentFaultInjectionVerifier f1pInjectionVerifier,
            ShippingFaultInjectionVerifier f2InjectionVerifier,
            PaymentF3FaultInjectionVerifier f3InjectionVerifier) {
        Map<String, FaultController> map = new LinkedHashMap<>();
        map.put(F0FaultController.PROFILE_F0, new F0FaultController(clock));
        map.put(
                F1PFaultController.PROFILE_F1_P,
                new F1PFaultController(clock, f1pInjectionVerifier));
        map.put(F2FaultController.PROFILE_F2, new F2FaultController(clock, f2InjectionVerifier));
        map.put(
                F3RecoveredFaultController.PROFILE,
                new F3RecoveredFaultController(clock, f3InjectionVerifier));
        map.put(
                F3UnresolvedFaultController.PROFILE,
                new F3UnresolvedFaultController(clock, f3InjectionVerifier));
        return new CompositeFaultController(map);
    }

    /** Profile ids registered in this composite (deterministic iteration order). */
    public Set<String> registeredProfiles() {
        return byProfile.keySet();
    }

    @Override
    public ArmedFaultRecord arm(FaultArmRequest request) {
        return delegate(request.faultProfileId()).arm(request);
    }

    @Override
    public FaultVerifyResult verify(ArmedFaultRecord armed, TrialSpec trial) {
        if (armed == null) {
            return FaultVerifyResult.failed("missing armed record");
        }
        return delegate(armed.faultProfileId()).verify(armed, trial);
    }

    @Override
    public void disarm(String trialId) {
        for (FaultController controller : byProfile.values()) {
            controller.disarm(trialId);
        }
    }

    @Override
    public FaultProfileDescriptor describe(String faultProfileId) {
        return delegate(faultProfileId).describe(faultProfileId);
    }

    private FaultController delegate(String faultProfileId) {
        FaultController controller = byProfile.get(faultProfileId);
        if (controller == null) {
            throw new IllegalArgumentException(
                    "unsupported fault profile '" + faultProfileId + "'; supported="
                            + byProfile.keySet());
        }
        return controller;
    }
}
