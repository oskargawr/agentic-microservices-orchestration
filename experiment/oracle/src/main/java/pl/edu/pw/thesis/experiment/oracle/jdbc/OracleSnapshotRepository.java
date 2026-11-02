package pl.edu.pw.thesis.experiment.oracle.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.experiment.oracle.snapshot.BusinessSnapshot;
import pl.edu.pw.thesis.experiment.oracle.snapshot.OperationLogEntry;
import pl.edu.pw.thesis.experiment.oracle.snapshot.PaymentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ReservationView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.ShipmentView;
import pl.edu.pw.thesis.experiment.oracle.snapshot.StockView;

/**
 * Read-only multi-schema snapshot loader.
 * Uses a single connection, {@code READ ONLY} + {@code REPEATABLE READ}, so Inventory /
 * Payment / Shipping are observed at one consistent transactional snapshot.
 */
public final class OracleSnapshotRepository {

    private final DataSource dataSource;

    public OracleSnapshotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BusinessSnapshot load(UUID orderId, String sku) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try {
                try (var st = connection.createStatement()) {
                    st.execute("SET TRANSACTION READ ONLY");
                }

                List<ReservationView> reservations = loadReservations(connection, orderId);
                Optional<PaymentView> payment = loadPayment(connection, orderId);
                Optional<ShipmentView> shipment = loadShipment(connection, orderId);
                // I1: stock.held must equal sum of qty over all HELD reservations for the SKU
                int heldSumSku = sumHeldQtyForSku(connection, sku);
                Optional<StockView> stock = loadStock(connection, sku, heldSumSku);
                List<OperationLogEntry> logs = loadOperationLogs(connection, orderId);

                connection.commit();
                return new BusinessSnapshot(
                        orderId, sku, reservations, payment, shipment, stock, logs);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static List<ReservationView> loadReservations(Connection c, UUID orderId)
            throws SQLException {
        String sql =
                """
                SELECT reservation_id, order_id, sku, qty, state
                FROM inventory.reservations
                WHERE order_id = ?
                ORDER BY created_at ASC, reservation_id ASC
                """;
        List<ReservationView> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ReservationView(
                            rs.getObject("reservation_id", UUID.class),
                            rs.getObject("order_id", UUID.class),
                            rs.getString("sku"),
                            rs.getInt("qty"),
                            ReservationState.valueOf(rs.getString("state"))));
                }
            }
        }
        return rows;
    }

    private static int sumHeldQtyForSku(Connection c, String sku) throws SQLException {
        String sql =
                """
                SELECT COALESCE(SUM(qty), 0)
                FROM inventory.reservations
                WHERE sku = ? AND state = 'HELD'
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Optional<PaymentView> loadPayment(Connection c, UUID orderId) throws SQLException {
        String sql =
                """
                SELECT payment_id, order_id, amount_minor, currency, state
                FROM payment.payments
                WHERE order_id = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PaymentView(
                        rs.getObject("payment_id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        PaymentState.valueOf(rs.getString("state"))));
            }
        }
    }

    private static Optional<ShipmentView> loadShipment(Connection c, UUID orderId) throws SQLException {
        String sql =
                """
                SELECT shipment_id, order_id, state
                FROM shipping.shipments
                WHERE order_id = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ShipmentView(
                        rs.getObject("shipment_id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        ShipmentState.valueOf(rs.getString("state"))));
            }
        }
    }

    private static Optional<StockView> loadStock(Connection c, String sku, int heldSum)
            throws SQLException {
        String sql = "SELECT sku, on_hand, held FROM inventory.stock WHERE sku = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StockView(
                        rs.getString("sku"), rs.getInt("on_hand"), rs.getInt("held"), heldSum));
            }
        }
    }

    private static List<OperationLogEntry> loadOperationLogs(Connection c, UUID orderId)
            throws SQLException {
        List<OperationLogEntry> logs = new ArrayList<>();
        loadLogSchema(
                c,
                orderId,
                "inventory",
                """
                SELECT id, operation, reservation_id AS resource_id, order_id, detail, created_at
                FROM inventory.operation_log WHERE order_id = ? ORDER BY id
                """,
                logs);
        loadLogSchema(
                c,
                orderId,
                "payment",
                """
                SELECT id, operation, payment_id AS resource_id, order_id, detail, created_at
                FROM payment.operation_log WHERE order_id = ? ORDER BY id
                """,
                logs);
        loadLogSchema(
                c,
                orderId,
                "shipping",
                """
                SELECT id, operation, shipment_id AS resource_id, order_id, detail, created_at
                FROM shipping.operation_log WHERE order_id = ? ORDER BY id
                """,
                logs);
        return logs;
    }

    private static void loadLogSchema(
            Connection c, UUID orderId, String schema, String sql, List<OperationLogEntry> out)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    Instant created = ts == null ? Instant.EPOCH : ts.toInstant();
                    out.add(new OperationLogEntry(
                            schema,
                            rs.getLong("id"),
                            rs.getString("operation"),
                            rs.getObject("resource_id", UUID.class),
                            rs.getObject("order_id", UUID.class),
                            rs.getString("detail"),
                            created));
                }
            }
        }
    }
}
