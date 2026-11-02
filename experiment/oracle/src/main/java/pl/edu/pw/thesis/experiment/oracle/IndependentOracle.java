package pl.edu.pw.thesis.experiment.oracle;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import javax.sql.DataSource;
import pl.edu.pw.thesis.experiment.oracle.classify.ClassificationDecision;
import pl.edu.pw.thesis.experiment.oracle.classify.OutcomeClassifier;
import pl.edu.pw.thesis.experiment.oracle.jdbc.OracleSnapshotRepository;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;

/**
 * TH-401 Independent Oracle — DB-primary, read-only, Temporal/LLM-agnostic.
 */
public final class IndependentOracle {

    private final OracleSnapshotRepository repository;
    private final OutcomeClassifier classifier;

    public IndependentOracle(DataSource dataSource) {
        this(new OracleSnapshotRepository(Objects.requireNonNull(dataSource, "dataSource")),
                new OutcomeClassifier());
    }

    IndependentOracle(OracleSnapshotRepository repository, OutcomeClassifier classifier) {
        this.repository = repository;
        this.classifier = Objects.requireNonNull(classifier, "classifier");
    }

    /** Classifier-only instance for unit tests that supply {@link BusinessSnapshot} fixtures. */
    public static IndependentOracle classifierOnly() {
        return new IndependentOracle((OracleSnapshotRepository) null, new OutcomeClassifier());
    }

    /** Classify from an already-loaded snapshot (unit tests / independence fixtures). */
    public OracleResult evaluateSnapshot(
            BusinessSnapshot snapshot, String workflowId, String trialId) {
        ClassificationDecision decision = classifier.classify(snapshot);
        return new OracleResult(
                decision.classification(),
                decision.pattern(),
                decision.reasonCode(),
                decision.violatedInvariants(),
                decision.warnings(),
                snapshot,
                workflowId,
                trialId,
                OracleResult.EVALUATOR_VERSION,
                Instant.now());
    }

    public OracleResult evaluate(OracleEvaluationRequest request) throws SQLException {
        if (repository == null) {
            throw new IllegalStateException("JDBC repository not configured; use evaluateSnapshot");
        }
        BusinessSnapshot snapshot = repository.load(request.orderId(), request.sku());
        return evaluateSnapshot(snapshot, request.workflowId(), request.trialId());
    }

    public OracleResult evaluate(java.util.UUID orderId, String sku) throws SQLException {
        return evaluate(OracleEvaluationRequest.of(orderId, sku));
    }
}
