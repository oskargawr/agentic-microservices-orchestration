package pl.edu.pw.thesis.experiment.runner.reset;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.config.UnsafeEnvironmentException;

/**
 * Deterministic business-DB reset to protocol baseline. Always checks allowlist first.
 */
public final class StateReset {

    private final RunnerEnvironment environment;
    private final DataSource dataSource;

    public StateReset(RunnerEnvironment environment, DataSource dataSource) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public BaselineSnapshot resetToProtocolBaseline(String sku) throws SQLException {
        environment.assertSafeForDestructiveOps();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                exec(c, "SET search_path TO inventory");
                exec(c, "DELETE FROM operation_log");
                exec(c, "DELETE FROM idempotency_records");
                exec(c, "DELETE FROM reservations");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE stock SET on_hand = 100, held = 0 WHERE sku = ?")) {
                    ps.setString(1, sku);
                    int updated = ps.executeUpdate();
                    if (updated != 1) {
                        throw new UnsafeEnvironmentException(
                                "BASELINE_VERIFY_FAILED",
                                "Expected exactly one stock row for sku=" + sku + ", updated=" + updated);
                    }
                }

                exec(c, "SET search_path TO payment");
                exec(c, "DELETE FROM operation_log");
                exec(c, "DELETE FROM idempotency_records");
                exec(c, "DELETE FROM payments");

                exec(c, "SET search_path TO shipping");
                exec(c, "DELETE FROM operation_log");
                exec(c, "DELETE FROM idempotency_records");
                exec(c, "DELETE FROM shipments");

                exec(c, "SET search_path TO inventory");
                int onHand;
                int held;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT on_hand, held FROM stock WHERE sku = ?")) {
                    ps.setString(1, sku);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new UnsafeEnvironmentException(
                                    "BASELINE_VERIFY_FAILED", "Missing stock row for sku=" + sku);
                        }
                        onHand = rs.getInt(1);
                        held = rs.getInt(2);
                    }
                }
                if (onHand != 100 || held != 0) {
                    throw new UnsafeEnvironmentException(
                            "BASELINE_VERIFY_FAILED",
                            "Baseline mismatch onHand=" + onHand + " held=" + held);
                }
                c.commit();
                return new BaselineSnapshot(sku, onHand, held);
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    public record BaselineSnapshot(String sku, int onHand, int held) {}
}
