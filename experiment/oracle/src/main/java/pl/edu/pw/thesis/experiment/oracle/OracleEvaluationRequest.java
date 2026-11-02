package pl.edu.pw.thesis.experiment.oracle;

import java.util.Objects;
import java.util.UUID;

/**
 * Oracle evaluation input. Only {@code orderId} and {@code sku} affect classification.
 * {@code workflowId}/{@code trialId} are metadata for harness artifacts.
 */
public record OracleEvaluationRequest(
        UUID orderId, String sku, String workflowId, String trialId) {

    public OracleEvaluationRequest {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
    }

    public static OracleEvaluationRequest of(UUID orderId, String sku) {
        return new OracleEvaluationRequest(orderId, sku, null, null);
    }
}
