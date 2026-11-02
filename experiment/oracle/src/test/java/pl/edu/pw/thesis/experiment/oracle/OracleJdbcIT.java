package pl.edu.pw.thesis.experiment.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;

/**
 * Optional JDBC integration against local Compose Postgres (:5433).
 * Skips cleanly when DB is unreachable so unit-only CI modules still pass offline.
 */
class OracleJdbcIT {

    private static final String JDBC = System.getenv()
            .getOrDefault(
                    "VARIANT_A_TEST_JDBC_URL", "jdbc:postgresql://localhost:5433/business");
    private static final String USER = "thesis";
    private static final String PASS = "thesis";
    private static final String SKU = "SKU-MVP-1";

    private static boolean dbUp;
    private UUID orderId;

    @BeforeAll
    static void checkDb() {
        try (Connection c = DriverManager.getConnection(JDBC, USER, PASS);
                Statement st = c.createStatement()) {
            st.execute("SELECT 1");
            dbUp = true;
        } catch (Exception e) {
            dbUp = false;
        }
    }

    @BeforeEach
    void requireDb() {
        assumeTrue(dbUp, "Postgres not available at " + JDBC);
        orderId = UUID.randomUUID();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (!dbUp) {
            return;
        }
        try (Connection c = DriverManager.getConnection(JDBC, USER, PASS);
                Statement st = c.createStatement()) {
            st.execute("DELETE FROM shipping.operation_log WHERE order_id = '" + orderId + "'");
            st.execute("DELETE FROM shipping.shipments WHERE order_id = '" + orderId + "'");
            st.execute("DELETE FROM payment.operation_log WHERE order_id = '" + orderId + "'");
            st.execute("DELETE FROM payment.payments WHERE order_id = '" + orderId + "'");
            st.execute("DELETE FROM inventory.operation_log WHERE order_id = '" + orderId + "'");
            st.execute("DELETE FROM inventory.reservations WHERE order_id = '" + orderId + "'");
        }
    }

    @Test
    void jdbc_f0_emptyOrder_readOnlyEvaluate() throws Exception {
        DataSource ds = dataSource();
        IndependentOracle oracle = new IndependentOracle(ds);
        OracleResult result = oracle.evaluate(orderId, SKU);
        assertThat(result.classification()).isEqualTo(BusinessOutcome.CONFIRMED_FAILURE);
        assertThat(result.pattern()).isEqualTo(OraclePattern.F0);
    }

    @Test
    void jdbc_successSnapshot_roundTrip() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(JDBC, USER, PASS);
                Statement st = c.createStatement()) {
            // Ensure stock exists (Flyway seed); adjust held temporarily then restore via cleanup of reservation
            st.execute(
                    """
                    INSERT INTO inventory.reservations
                      (reservation_id, order_id, sku, qty, state)
                    VALUES ('%s', '%s', '%s', 1, 'CONSUMED')
                    """
                            .formatted(reservationId, orderId, SKU));
            st.execute(
                    """
                    INSERT INTO payment.payments
                      (payment_id, order_id, amount_minor, currency, state)
                    VALUES ('%s', '%s', 2599, 'PLN', 'CAPTURED')
                    """
                            .formatted(paymentId, orderId));
            st.execute(
                    """
                    INSERT INTO shipping.shipments
                      (shipment_id, order_id, state)
                    VALUES ('%s', '%s', 'ARRANGED')
                    """
                            .formatted(shipmentId, orderId));
        }

        IndependentOracle oracle = new IndependentOracle(dataSource());
        OracleResult result = oracle.evaluate(OracleEvaluationRequest.of(orderId, SKU));
        assertThat(result.classification()).isEqualTo(BusinessOutcome.CONFIRMED_SUCCESS);
        assertThat(result.pattern()).isEqualTo(OraclePattern.SUCCESS);
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(JDBC);
        ds.setUser(USER);
        ds.setPassword(PASS);
        return ds;
    }
}
