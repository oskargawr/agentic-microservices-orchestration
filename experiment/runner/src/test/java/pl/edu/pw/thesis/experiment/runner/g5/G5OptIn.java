package pl.edu.pw.thesis.experiment.runner.g5;

/**
 * Opt-in gate for live G5 Temporal runner scenarios (D-AUTH-G5).
 *
 * <p>Default Gradle/test runs must leave {@code THESIS_RUN_G5} unset so G5 live IT is disabled.
 * Live execution remains separately Owner-authorized even when the flag is present.
 */
public final class G5OptIn {

    public static final String ENV_RUN_G5 = "THESIS_RUN_G5";
    public static final String ENV_SCENARIO = "THESIS_RUN_G5_SCENARIO";

    private G5OptIn() {}

    public static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    /** True when the process environment requests G5 live IT. */
    public static boolean liveG5Requested() {
        return isTruthy(System.getenv(ENV_RUN_G5));
    }

    /**
     * Scenario selector: {@code G5-2}, {@code G5-3}, or {@code ALL} (default when unset and live
     * requested).
     */
    public static String requestedScenario() {
        String raw = System.getenv(ENV_SCENARIO);
        if (raw == null || raw.isBlank()) {
            return "ALL";
        }
        return raw.trim();
    }

    public static boolean scenarioEnabled(String scenarioId) {
        if (!liveG5Requested()) {
            return false;
        }
        String sel = requestedScenario();
        return "ALL".equalsIgnoreCase(sel) || scenarioId.equalsIgnoreCase(sel);
    }
}
