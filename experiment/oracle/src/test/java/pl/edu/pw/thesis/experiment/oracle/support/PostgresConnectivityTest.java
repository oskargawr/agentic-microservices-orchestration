package pl.edu.pw.thesis.experiment.oracle.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class PostgresConnectivityTest {

    @Test
    void sqlState08_isUnreachable() {
        assertThat(PostgresConnectivity.isUnreachable(new SQLException("refused", "08001")))
                .isTrue();
        assertThat(PostgresConnectivity.isUnreachable(new SQLException("failure", "08006")))
                .isTrue();
    }

    @Test
    void authSqlState28_isNotUnreachable() {
        assertThat(PostgresConnectivity.isUnreachable(
                        new SQLException("password authentication failed", "28P01")))
                .isFalse();
        assertThat(PostgresConnectivity.isUnreachable(new SQLException("invalid auth", "28000")))
                .isFalse();
    }

    @Test
    void connectExceptionCause_isUnreachable() {
        SQLException wrapped =
                new SQLException("Connection to localhost:5433 refused", null, new ConnectException());
        assertThat(PostgresConnectivity.isUnreachable(wrapped)).isTrue();
    }

    @Test
    void genericSqlWithoutNetwork_isNotUnreachable() {
        assertThat(PostgresConnectivity.isUnreachable(
                        new SQLException("relation does not exist", "42P01")))
                .isFalse();
    }
}
