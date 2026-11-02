package pl.edu.pw.thesis.orchestration.console;

/**
 * TH-206 gate for {@link ConsoleRealTemporalE2EIT}.
 *
 * <p>Local (non-CI): optional stack — return false to skip when Postgres/Temporal are down.
 * CI / explicit require: never skip; missing infra fails hard.
 */
final class ConsoleTemporalE2EGate {

    private ConsoleTemporalE2EGate() {
    }

    /** GitHub Actions sets {@code CI=true}; {@code THESIS_REQUIRE_TEMPORAL_E2E} forces the same locally. */
    static boolean requireTemporalE2E(String ciEnv, String thesisRequireEnv) {
        return isTruthy(ciEnv) || isTruthy(thesisRequireEnv);
    }

    /**
     * @return {@code true} to enable the test class
     * @throws IllegalStateException when required in CI and stack is incomplete
     */
    static boolean shouldEnable(boolean require, boolean postgresOk, boolean temporalOk) {
        if (require) {
            if (!postgresOk || !temporalOk) {
                throw new IllegalStateException(
                        "ConsoleRealTemporalE2EIT is required (CI/THESIS_REQUIRE_TEMPORAL_E2E) but stack is"
                                + " unavailable (postgresOk=" + postgresOk + ", temporalOk=" + temporalOk
                                + "). Silent skip is forbidden.");
            }
            return true;
        }
        return postgresOk && temporalOk;
    }

    static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }
}
