package pl.edu.pw.thesis.experiment.runner.reset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import pl.edu.pw.thesis.experiment.runner.config.JdbcAllowlist;
import pl.edu.pw.thesis.experiment.runner.config.RunnerEnvironment;
import pl.edu.pw.thesis.experiment.runner.config.UnsafeEnvironmentException;
import pl.edu.pw.thesis.experiment.runner.model.RunnerTiming;
import pl.edu.pw.thesis.experiment.runner.support.PostgresConnectivity;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

/**
 * G1/G2 — real PostgreSQL connection-metadata gate + StateReset on allowlisted lab DB.
 */
class StateResetJdbcIT {

    private static final String BUSINESS_JDBC = System.getenv()
            .getOrDefault(
                    "VARIANT_A_TEST_JDBC_URL", "jdbc:postgresql://localhost:5433/business");
    private static final String POSTGRES_JDBC =
            System.getenv()
                    .getOrDefault("THESIS_POSTGRES_JDBC_URL", "jdbc:postgresql://localhost:5433/postgres");
    private static final String USER = System.getenv().getOrDefault("VARIANT_A_TEST_USER", "thesis");
    private static final String PASS =
            System.getenv().getOrDefault("VARIANT_A_TEST_PASSWORD", "thesis");
    private static final String SKU = "SKU-MVP-1";

    private static boolean dbUp;

    @TempDir Path tmp;

    @BeforeAll
    static void beforeAll() throws Exception {
        try (Connection c = DriverManager.getConnection(BUSINESS_JDBC, USER, PASS);
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
        migrateSchema("inventory", "classpath:db/migration/inventory");
        migrateSchema("payment", "classpath:db/migration/payment");
        migrateSchema("shipping", "classpath:db/migration/shipping");
    }

    private static void migrateSchema(String schema, String location) {
        Flyway.configure()
                .dataSource(BUSINESS_JDBC, USER, PASS)
                .schemas(schema)
                .defaultSchema(schema)
                .locations(location)
                .load()
                .migrate();
    }

    @BeforeEach
    void requireDb() {
        assumeTrue(dbUp, "Postgres unreachable at " + BUSINESS_JDBC);
    }

    @Test
    void g1_connectionMetadataMismatchRejectedBeforeDestructiveSql() throws Exception {
        long businessOnHandBefore = readBusinessOnHand();
        long businessReservationsBefore = countBusiness("inventory", "reservations");
        long sentinel = System.nanoTime();
        seedWrongTargetProbe(sentinel);

        RunnerEnvironment env = environment(BUSINESS_JDBC, JdbcAllowlist.of(BUSINESS_JDBC));
        DataSource wrongDs = dataSource(POSTGRES_JDBC);
        StateReset reset = new StateReset(env, wrongDs);

        assertThatThrownBy(() -> reset.resetToProtocolBaseline(SKU))
                .isInstanceOf(UnsafeEnvironmentException.class)
                .extracting(ex -> ((UnsafeEnvironmentException) ex).exclusionReason())
                .isEqualTo("WRONG_DATABASE");

        assertThat(readWrongTargetProbe())
                .as("wrong-target probe row must be unchanged after rejected reset")
                .isEqualTo(sentinel);
        assertThat(readBusinessOnHand())
                .as("allowlisted business stock must be unchanged")
                .isEqualTo(businessOnHandBefore);
        assertThat(countBusiness("inventory", "reservations"))
                .as("allowlisted business reservations must be unchanged")
                .isEqualTo(businessReservationsBefore);
    }

    @Test
    void g2_stateResetOnAllowlistedDbIsIdempotent() throws Exception {
        RunnerEnvironment env = environment(BUSINESS_JDBC, JdbcAllowlist.of(BUSINESS_JDBC));
        DataSource ds = dataSource(BUSINESS_JDBC);
        StateReset reset = new StateReset(env, ds);

        StateReset.BaselineSnapshot first = reset.resetToProtocolBaseline(SKU);
        StateReset.BaselineSnapshot second = reset.resetToProtocolBaseline(SKU);

        assertThat(first.onHand()).isEqualTo(100);
        assertThat(first.held()).isEqualTo(0);
        assertThat(second.onHand()).isEqualTo(100);
        assertThat(second.held()).isEqualTo(0);
    }

    private static void seedWrongTargetProbe(long sentinel) throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES_JDBC, USER, PASS);
                Statement st = c.createStatement()) {
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS public.th402_gate_probe(
                      k text PRIMARY KEY,
                      v bigint NOT NULL
                    )
                    """);
            st.execute(
                    "INSERT INTO public.th402_gate_probe(k, v) VALUES ('g1', "
                            + sentinel
                            + ") ON CONFLICT (k) DO UPDATE SET v = EXCLUDED.v");
        }
    }

    private static long readWrongTargetProbe() throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES_JDBC, USER, PASS);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT v FROM public.th402_gate_probe WHERE k = 'g1'")) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private static long readBusinessOnHand() throws SQLException {
        try (Connection c = DriverManager.getConnection(BUSINESS_JDBC, USER, PASS);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT on_hand FROM inventory.stock WHERE sku = '" + SKU + "'")) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private static long countBusiness(String schema, String table) throws SQLException {
        try (Connection c = DriverManager.getConnection(BUSINESS_JDBC, USER, PASS);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + schema + "." + table)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private RunnerEnvironment environment(String jdbcUrl, JdbcAllowlist allowlist) {
        return new RunnerEnvironment(
                "lab-it",
                jdbcUrl,
                USER,
                PASS,
                allowlist,
                tmp,
                new TemporalSettings("localhost:7233", "default"),
                new RunnerTiming(Duration.ofSeconds(30), Duration.ofSeconds(120), Duration.ofSeconds(5)));
    }

    private static DataSource dataSource(String jdbcUrl) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(jdbcUrl);
        ds.setUser(USER);
        ds.setPassword(PASS);
        return ds;
    }
}
