package pl.edu.pw.thesis.inventory.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;

@Repository
public class InventoryRepository {

    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Transaction-scoped advisory lock on idempotency key.
     * Serializes concurrent requests that share a key before business effects run.
     */
    public void advisoryLockKey(String idempotencyKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, idempotencyKey);
    }

    public Optional<StockRow> findStockForUpdate(String sku) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT sku, on_hand, held FROM stock WHERE sku = ? FOR UPDATE",
                    (rs, i) -> new StockRow(rs.getString("sku"), rs.getInt("on_hand"), rs.getInt("held")),
                    sku));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<StockRow> findStock(String sku) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT sku, on_hand, held FROM stock WHERE sku = ?",
                    (rs, i) -> new StockRow(rs.getString("sku"), rs.getInt("on_hand"), rs.getInt("held")),
                    sku));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updateStock(String sku, int onHand, int held) {
        int updated = jdbc.update(
                "UPDATE stock SET on_hand = ?, held = ? WHERE sku = ?",
                onHand, held, sku);
        if (updated != 1) {
            throw new IllegalStateException("stock update affected " + updated + " rows for " + sku);
        }
    }

    public void insertReservation(ReservationRow row) {
        jdbc.update(
                """
                INSERT INTO reservations (reservation_id, order_id, sku, qty, state)
                VALUES (?, ?, ?, ?, ?)
                """,
                row.reservationId(), row.orderId(), row.sku(), row.qty(), row.state().name());
    }

    public Optional<ReservationRow> findReservationForUpdate(UUID reservationId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT reservation_id, order_id, sku, qty, state
                    FROM reservations WHERE reservation_id = ? FOR UPDATE
                    """,
                    (rs, i) -> new ReservationRow(
                            (UUID) rs.getObject("reservation_id"),
                            (UUID) rs.getObject("order_id"),
                            rs.getString("sku"),
                            rs.getInt("qty"),
                            ReservationState.valueOf(rs.getString("state"))),
                    reservationId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ReservationRow> findReservation(UUID reservationId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT reservation_id, order_id, sku, qty, state
                    FROM reservations WHERE reservation_id = ?
                    """,
                    (rs, i) -> new ReservationRow(
                            (UUID) rs.getObject("reservation_id"),
                            (UUID) rs.getObject("order_id"),
                            rs.getString("sku"),
                            rs.getInt("qty"),
                            ReservationState.valueOf(rs.getString("state"))),
                    reservationId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updateReservationState(UUID reservationId, ReservationState state) {
        int updated = jdbc.update(
                "UPDATE reservations SET state = ?, updated_at = NOW() WHERE reservation_id = ?",
                state.name(), reservationId);
        if (updated != 1) {
            throw new IllegalStateException("reservation update affected " + updated + " rows");
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
            UUID reservationId,
            UUID orderId,
            String sku,
            Integer qty,
            String idempotencyKey,
            String detail) {
        jdbc.update(
                """
                INSERT INTO operation_log
                    (operation, reservation_id, order_id, sku, qty, idempotency_key, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                operation, reservationId, orderId, sku, qty, idempotencyKey, detail);
    }
}
