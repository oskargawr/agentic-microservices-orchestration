package pl.edu.pw.thesis.experiment.runner.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class JdbcAllowlistTest {

    @Test
    void emptyAllowlistFailsClosed() {
        JdbcAllowlist allowlist = new JdbcAllowlist(Set.of());
        assertThatThrownBy(() -> allowlist.requireAllowed("jdbc:postgresql://localhost:5433/business"))
                .isInstanceOf(UnsafeEnvironmentException.class)
                .extracting(ex -> ((UnsafeEnvironmentException) ex).exclusionReason())
                .isEqualTo("WRONG_DATABASE");
    }

    @Test
    void acceptsNormalizedAllowlistedUrl() {
        JdbcAllowlist allowlist = JdbcAllowlist.of("jdbc:postgresql://localhost:5433/business");
        assertThatCode(() -> allowlist.requireAllowed(
                        "jdbc:postgresql://localhost:5433/business?ssl=false"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownDatabase() {
        JdbcAllowlist allowlist = JdbcAllowlist.of("jdbc:postgresql://localhost:5433/business");
        assertThatThrownBy(() -> allowlist.requireAllowed("jdbc:postgresql://localhost:5433/production"))
                .isInstanceOf(UnsafeEnvironmentException.class)
                .hasMessageContaining("not on allowlist");
    }
}
