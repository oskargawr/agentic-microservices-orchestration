package pl.edu.pw.thesis.experiment.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import pl.edu.pw.thesis.contracts.oracle.BusinessOutcome;
import pl.edu.pw.thesis.experiment.oracle.support.PostgresConnectivity;

/**
 * JDBC integration against Compose Postgres (:5433).
 *
 * <p>Does <strong>not</strong> depend on other modules' tests having run first. Canonical
 * Flyway scripts from each service {@code db/migration} folder are applied in
 * {@code beforeAll} into schemas (fresh CI Compose or already-migrated local DB).
 *
 * <p>Skips only when PostgreSQL is genuinely unreachable (SQLState {@code 08*} / network
 * cause). Auth and configuration errors fail the suite. Flyway failures are never skipped.
 *
 * <p>Flyway {@code baselineOnMigrate} is <strong>not</strong> enabled: empty schemas migrate
 * from scratch; already-migrated schemas are idempotent via history; non-empty schemas
 * without history must fail loudly rather than baseline and skip V1.
 */
class OracleJdbcIT {

    private static final String JDBC = System.getenv()
            .getOrDefault(
                    "VARIANT_A_TEST_JDBC_URL", "jdbc:postgresql://localhost:5433/business");
    private static final String USER = System.getenv().getOrDefault("VARIANT_A_TEST_USER", "thesis");
    private static final String PASS =
            System.getenv().getOrDefault("VARIANT_A_TEST_PASSWORD", "thesis");
    private static final String SKU = "SKU-MVP-1";

    private static boolean dbUp;
    private UUID orderId;

    @BeforeAll
    static void beforeAll() throws Exception {
        try (Connection c = DriverManager.getConnection(JDBC, USER, PASS);
                Statement st = c.createStatement()) {
            st.execute("SELECT 1");
            dbUp = true;
        } catch (SQLException e) {
            if (PostgresConnectivity.isUnreachable(e)) {
                dbUp = false;
                return;
            }
            throw e;
        }
        // Must not be skipped: migrate failures propagate.
        migrateSchema("inventory", "classpath:db/migration/inventory");
        migrateSchema("payment", "classpath:db/migration/payment");
        migrateSchema("shipping", "classpath:db/migration/shipping");
    }

    private static void migrateSchema(String schema, String location) {
        Flyway.configure()
                .dataSource(JDBC, USER, PASS)
                .schemas(schema)
                .defaultSchema(schema)
                .locations(location)
                .load()
                .migrate();
    }

    @BeforeEach
    void requireDb() {
        assumeTrue(dbUp, "Postgres unreachable at " + JDBC);
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
        assertThat(result.snapshot().stock()).isPresent();
    }

    @Test
    void jdbc_successSnapshot_roundTrip() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(JDBC, USER, PASS);
                Statement st = c.createStatement()) {
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
        assertThat(result.snapshot().stock()).isPresent();
        assertThat(result.snapshot().payment()).isPresent();
        assertThat(result.snapshot().shipment()).isPresent();
        assertThat(result.snapshot().singleReservation()).isPresent();
    }

    /**
     * When Postgres is reachable, bad credentials must surface as a failing SQLException
     * (auth SQLState), not as an "unreachable" skip signal.
     */
    @Test
    void invalidCredentials_areNotTreatedAsUnreachable() {
        assumeTrue(dbUp, "requires reachable Postgres to contrast with auth failure");
        assertThatThrownBy(() -> DriverManager.getConnection(JDBC, USER, "definitely-wrong-password"))
                .isInstanceOf(SQLException.class)
                .satisfies(ex -> {
                    SQLException sql = (SQLException) ex;
                    assertThat(PostgresConnectivity.isUnreachable(sql))
                            .as("SQLState=%s", sql.getSQLState())
                            .isFalse();
                    assertThat(sql.getSQLState()).startsWith("28");
                });
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(JDBC);
        ds.setUser(USER);
        ds.setPassword(PASS);
        return ds;
    }
}
