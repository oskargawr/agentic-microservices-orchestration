package pl.edu.pw.thesis.experiment.runner.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;

/**
 * Fail-closed connection-metadata gate (design §8.1 / D-R12 / phase-1.1 finding A).
 * Configured URL allowlist remains mandatory; live connection identity must also match.
 */
public final class JdbcConnectionGate {

    private JdbcConnectionGate() {}

    /**
     * Validates {@code expectedJdbcUrl} against the allowlist, then verifies the live
     * {@link Connection} metadata URL and {@code current_database()} agree. No destructive
     * SQL may run before this returns successfully.
     */
    public static void requireAllowedConnection(
            Connection connection, JdbcAllowlist allowlist, String expectedJdbcUrl)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(allowlist, "allowlist");
        Objects.requireNonNull(expectedJdbcUrl, "expectedJdbcUrl");

        allowlist.requireAllowed(expectedJdbcUrl);

        String metaUrl;
        try {
            metaUrl = connection.getMetaData().getURL();
        } catch (SQLException e) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "Cannot read JDBC connection metadata URL: " + e.getMessage());
        }
        if (metaUrl == null || metaUrl.isBlank()) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "JDBC connection metadata URL is missing — fail-closed");
        }

        try {
            allowlist.requireAllowed(metaUrl);
        } catch (UnsafeEnvironmentException e) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "Live connection URL not on allowlist: " + metaUrl);
        }

        String expectedNorm = JdbcAllowlist.normalize(expectedJdbcUrl);
        String liveNorm = JdbcAllowlist.normalize(metaUrl);
        if (!expectedNorm.equals(liveNorm)) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "Configured JDBC URL and live connection metadata disagree: configured="
                            + expectedNorm
                            + " live="
                            + liveNorm);
        }

        String expectedDb = databaseNameFromNormalized(expectedNorm);
        String liveDb = queryCurrentDatabase(connection);
        if (liveDb == null || liveDb.isBlank()) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "current_database() unavailable — fail-closed");
        }
        if (!expectedDb.equalsIgnoreCase(liveDb.trim())) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "Database name mismatch: expected="
                            + expectedDb
                            + " current_database="
                            + liveDb);
        }
    }

    static String databaseNameFromNormalized(String normalized) {
        int slash = normalized.lastIndexOf('/');
        if (slash < 0 || slash == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(slash + 1).toLowerCase(Locale.ROOT);
    }

    private static String queryCurrentDatabase(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT current_database()")) {
            if (!rs.next()) {
                return null;
            }
            return rs.getString(1);
        } catch (SQLException e) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "Failed to query current_database(): " + e.getMessage());
        }
    }
}
