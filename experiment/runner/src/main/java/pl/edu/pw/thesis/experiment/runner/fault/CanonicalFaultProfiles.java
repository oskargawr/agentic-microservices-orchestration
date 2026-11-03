package pl.edu.pw.thesis.experiment.runner.fault;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Canonical TH-400 Variant A fault profile ids (S0 catalogue). */
public final class CanonicalFaultProfiles {

    public static final List<String> ALL = List.of(
            F0FaultController.PROFILE_F0,
            F1PFaultController.PROFILE_F1_P,
            F2FaultController.PROFILE_F2,
            F3RecoveredFaultController.PROFILE,
            F3UnresolvedFaultController.PROFILE);

    private CanonicalFaultProfiles() {}

    public static boolean isCanonical(String profileId) {
        return ALL.contains(profileId);
    }

    public static void requireCanonical(String profileId) {
        if ("F1".equals(profileId)) {
            throw new IllegalArgumentException(
                    "canonical profile id is F1-P (not F1); use F1-P");
        }
        if ("F3".equals(profileId)) {
            throw new IllegalArgumentException(
                    "canonical ids are F3-Recovered / F3-Unresolved (not F3)");
        }
        if (!isCanonical(profileId)) {
            throw new IllegalArgumentException(
                    "unsupported fault profile '" + profileId + "'; supported=" + ALL);
        }
    }

    public static List<String> normalizeSchedule(List<String> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            throw new IllegalArgumentException("faultProfileIds must not be empty");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String id : profileIds) {
            requireCanonical(id);
            seen.add(id);
        }
        return List.copyOf(seen);
    }

    public static boolean requiresPaymentBaseUrl(List<String> profileIds) {
        for (String id : profileIds) {
            if (F1PFaultController.PROFILE_F1_P.equals(id)
                    || F3RecoveredFaultController.PROFILE.equals(id)
                    || F3UnresolvedFaultController.PROFILE.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean requiresShippingBaseUrl(List<String> profileIds) {
        return profileIds.contains(F2FaultController.PROFILE_F2);
    }

    /** True when the schedule needs the full concurrent F0–F3 registration map. */
    public static boolean needsAllProfileRegistration(List<String> profileIds) {
        return profileIds.size() > 1
                || (!profileIds.isEmpty() && !F0FaultController.PROFILE_F0.equals(profileIds.getFirst()));
    }
}
