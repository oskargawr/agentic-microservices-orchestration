package pl.edu.pw.thesis.experiment.runner.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class JdbcConnectionGateTest {

    @Test
    void rejectsMissingMetadataUrl() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn(null);

        JdbcAllowlist allowlist = JdbcAllowlist.of("jdbc:postgresql://localhost:5433/business");
        assertThatThrownBy(() -> JdbcConnectionGate.requireAllowedConnection(
                        connection, allowlist, "jdbc:postgresql://localhost:5433/business"))
                .isInstanceOf(UnsafeEnvironmentException.class)
                .hasMessageContaining("metadata URL is missing");
    }

    @Test
    void rejectsCatalogMismatch() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        Statement st = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:postgresql://localhost:5433/business");
        when(connection.createStatement()).thenReturn(st);
        when(st.executeQuery("SELECT current_database()")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("production");

        JdbcAllowlist allowlist = JdbcAllowlist.of("jdbc:postgresql://localhost:5433/business");
        assertThatThrownBy(() -> JdbcConnectionGate.requireAllowedConnection(
                        connection, allowlist, "jdbc:postgresql://localhost:5433/business"))
                .isInstanceOf(UnsafeEnvironmentException.class)
                .hasMessageContaining("Database name mismatch");
    }
}
