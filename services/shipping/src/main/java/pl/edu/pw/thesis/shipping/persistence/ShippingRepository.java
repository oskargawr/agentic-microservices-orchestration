package pl.edu.pw.thesis.shipping.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

@Repository
public class ShippingRepository {

    private final JdbcTemplate jdbc;

    public ShippingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void advisoryLockKey(String idempotencyKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, idempotencyKey);
    }

    public Optional<ShipmentRow> findByIdForUpdate(UUID shipmentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT shipment_id, order_id, state FROM shipments
                    WHERE shipment_id = ? FOR UPDATE
                    """,
                    (rs, i) -> map(rs),
                    shipmentId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ShipmentRow> findById(UUID shipmentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT shipment_id, order_id, state FROM shipments WHERE shipment_id = ?
                    """,
                    (rs, i) -> map(rs),
                    shipmentId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insert(ShipmentRow row) {
        jdbc.update(
                """
                INSERT INTO shipments (shipment_id, order_id, state) VALUES (?, ?, ?)
                """,
                row.shipmentId(), row.orderId(), row.state().name());
    }

    public void updateState(UUID shipmentId, ShipmentState state) {
        int n = jdbc.update(
                "UPDATE shipments SET state = ?, updated_at = NOW() WHERE shipment_id = ?",
                state.name(), shipmentId);
        if (n != 1) {
            throw new IllegalStateException("shipment update affected " + n + " rows");
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
            UUID shipmentId,
            UUID orderId,
            String idempotencyKey,
            String detail) {
        jdbc.update(
                """
                INSERT INTO operation_log (operation, shipment_id, order_id, idempotency_key, detail)
                VALUES (?, ?, ?, ?, ?)
                """,
                operation, shipmentId, orderId, idempotencyKey, detail);
    }

    private static ShipmentRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ShipmentRow(
                (UUID) rs.getObject("shipment_id"),
                (UUID) rs.getObject("order_id"),
                ShipmentState.valueOf(rs.getString("state")));
    }
}
