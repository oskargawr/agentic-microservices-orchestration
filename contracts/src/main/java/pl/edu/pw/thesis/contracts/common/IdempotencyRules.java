package pl.edu.pw.thesis.contracts.common;

import java.util.Objects;

/**
 * Contract-level idempotency rules (TH-101.1).
 *
 * <p>Transport: HTTP header {@code Idempotency-Key} only (not request body).
 *
 * <ul>
 *   <li>Same key + same logical payload fingerprint → replay original result (no duplicate effects).</li>
 *   <li>Same key + different logical payload → {@link ErrorCode#IDEMPOTENCY_KEY_CONFLICT} (HTTP 409).</li>
 * </ul>
 *
 * <p>MVP scope: single SKU per order; recommended key values in {@link IdempotencyKeys}.
 */
public final class IdempotencyRules {

    public static final String HEADER = "Idempotency-Key";

    private IdempotencyRules() {
    }

    public static String requireKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }
        return idempotencyKey;
    }

    /**
     * When a key was already used, the new request's logical payload must match the stored fingerprint.
     *
     * @param storedPayloadFingerprint opaque fingerprint of the first request's logical fields
     * @param newPayloadFingerprint    fingerprint of the current request's logical fields
     */
    public static void assertPayloadCompatible(
            String idempotencyKey,
            Object storedPayloadFingerprint,
            Object newPayloadFingerprint) {
        requireKey(idempotencyKey);
        if (!Objects.equals(storedPayloadFingerprint, newPayloadFingerprint)) {
            throw new IdempotencyConflictException(
                    idempotencyKey,
                    "Idempotency-Key reuse with a different logical payload");
        }
    }
}
