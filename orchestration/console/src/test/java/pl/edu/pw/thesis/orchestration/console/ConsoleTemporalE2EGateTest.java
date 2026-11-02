package pl.edu.pw.thesis.orchestration.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConsoleTemporalE2EGateTest {

    @Test
    void localOptional_skipsWhenStackDown() {
        assertThat(ConsoleTemporalE2EGate.shouldEnable(false, false, false)).isFalse();
        assertThat(ConsoleTemporalE2EGate.shouldEnable(false, true, false)).isFalse();
        assertThat(ConsoleTemporalE2EGate.shouldEnable(false, false, true)).isFalse();
        assertThat(ConsoleTemporalE2EGate.shouldEnable(false, true, true)).isTrue();
    }

    @Test
    void ciRequired_enablesWhenStackUp() {
        assertThat(ConsoleTemporalE2EGate.shouldEnable(true, true, true)).isTrue();
    }

    @Test
    void ciRequired_failsHardWhenPostgresMissing() {
        assertThatThrownBy(() -> ConsoleTemporalE2EGate.shouldEnable(true, false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Silent skip is forbidden");
    }

    @Test
    void ciRequired_failsHardWhenTemporalMissing() {
        assertThatThrownBy(() -> ConsoleTemporalE2EGate.shouldEnable(true, true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("temporalOk=false");
    }

    @Test
    void requireFlag_readsCiOrThesisEnv() {
        assertThat(ConsoleTemporalE2EGate.requireTemporalE2E(null, null)).isFalse();
        assertThat(ConsoleTemporalE2EGate.requireTemporalE2E("true", null)).isTrue();
        assertThat(ConsoleTemporalE2EGate.requireTemporalE2E(null, "true")).isTrue();
        assertThat(ConsoleTemporalE2EGate.requireTemporalE2E("false", "1")).isTrue();
        assertThat(ConsoleTemporalE2EGate.requireTemporalE2E("0", "no")).isFalse();
    }
}
