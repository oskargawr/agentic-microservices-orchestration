package pl.edu.pw.thesis.payment.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.edu.pw.thesis.contracts.payment.PaymentState;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void advisoryLockKey(String idempotencyKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, idempotencyKey);
    }

    public void advisoryLockPayment(UUID paymentId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, "payment:" + paymentId);
    }

    public Optional<PaymentRow> findByIdForUpdate(UUID paymentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT payment_id, order_id, amount_minor, currency, state
                    FROM payments WHERE payment_id = ? FOR UPDATE
                    """,
                    (rs, i) -> mapPayment(rs),
                    paymentId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<PaymentRow> findById(UUID paymentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT payment_id, order_id, amount_minor, currency, state
                    FROM payments WHERE payment_id = ?
                    """,
                    (rs, i) -> mapPayment(rs),
                    paymentId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insert(PaymentRow row) {
        jdbc.update(
                """
                INSERT INTO payments (payment_id, order_id, amount_minor, currency, state)
                VALUES (?, ?, ?, ?, ?)
                """,
                row.paymentId(), row.orderId(), row.amountMinor(), row.currency(), row.state().name());
    }

    public void updateState(UUID paymentId, PaymentState state) {
        int n = jdbc.update(
                "UPDATE payments SET state = ?, updated_at = NOW() WHERE payment_id = ?",
                state.name(), paymentId);
        if (n != 1) {
            throw new IllegalStateException("payment update affected " + n + " rows");
        }
    }

    public Optional<IdempotencyRow> findIdempotencyForUpdate(String key) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT idempotency_key, operation, request_fingerprint, http_status, response_body
                    FROM idempotency_records WHERE idempotency_key = ? FOR UPDATE
                    """,
                    (rs, i) -> new IdempotencyRow(
                            rs.getString("idempotency_key"),
                            rs.getString("operation"),
                            rs.getString("request_fingerprint"),
                            rs.getInt("http_status"),
                            rs.getString("response_body")),
                    key));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insertIdempotency(IdempotencyRow row) {
        jdbc.update(
                """
                INSERT INTO idempotency_records
                    (idempotency_key, operation, request_fingerprint, http_status, response_body)
                VALUES (?, ?, ?, ?, ?)
                """,
                row.idempotencyKey(), row.operation(), row.requestFingerprint(),
                row.httpStatus(), row.responseBody());
    }

    public void appendOperationLog(
            String operation,
            UUID paymentId,
            UUID orderId,
            String idempotencyKey,
            String detail) {
        jdbc.update(
                """
                INSERT INTO operation_log (operation, payment_id, order_id, idempotency_key, detail)
                VALUES (?, ?, ?, ?, ?)
                """,
                operation, paymentId, orderId, idempotencyKey, detail);
    }

    private static PaymentRow mapPayment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PaymentRow(
                (UUID) rs.getObject("payment_id"),
                (UUID) rs.getObject("order_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                PaymentState.valueOf(rs.getString("state")));
    }
}
