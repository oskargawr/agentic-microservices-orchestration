package pl.edu.pw.thesis.payment.fault;

/**
 * Deterministic test/experiment fault codes via header {@code X-Thesis-Fault}.
 * Only honored when {@code thesis.payment.fault-injection-enabled=true}.
 */
public enum ThesisFault {
    /** Happy-path capture (default). */
    CHARGE_CAPTURE,
    /** Persist and return FAILED. */
    CHARGE_FAIL,
    /** Persist and return UNKNOWN (no capture). */
    CHARGE_UNKNOWN,
    /**
     * F3: persist CAPTURED (oracle truth) but return UNKNOWN on charge response
     * (caller observes timeout/lost response).
     */
    F3_CAPTURE_LOST_RESPONSE,
    /** Resolve probe: confirm capture. */
    RESOLVE_CAPTURE,
    /** Resolve probe: authoritative non-capture. */
    RESOLVE_FAIL,
    /** Resolve probe: inconclusive — keep UNKNOWN/PENDING. */
    RESOLVE_INCONCLUSIVE,
    /**
     * F3-Unresolved transport opacity: resolve/GET return HTTP 503 while header present.
     * Must not mutate payment DB state (OQ-F3U-2 / D-AUTH-S3-3).
     */
    RESOLVE_TRANSPORT_OPAQUE;

    public static final String HEADER = "X-Thesis-Fault";

    public static ThesisFault parseOrDefault(String raw, ThesisFault defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return ThesisFault.valueOf(raw.trim());
    }
}
