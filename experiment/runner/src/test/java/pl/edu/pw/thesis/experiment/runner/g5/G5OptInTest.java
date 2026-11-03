package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class G5OptInTest {

    @Test
    void truthyParsing() {
        assertThat(G5OptIn.isTruthy("true")).isTrue();
        assertThat(G5OptIn.isTruthy("1")).isTrue();
        assertThat(G5OptIn.isTruthy("yes")).isTrue();
        assertThat(G5OptIn.isTruthy("false")).isFalse();
        assertThat(G5OptIn.isTruthy(null)).isFalse();
        assertThat(G5OptIn.isTruthy("")).isFalse();
    }

    @Test
    void liveG5Requested_defaultsFalseInOrdinaryTestJvm() {
        // Delivery rule: ordinary unit/IT JVM must not enable live G5.
        assertThat(G5OptIn.liveG5Requested()).isFalse();
        assertThat(G5OptIn.scenarioEnabled("G5-2")).isFalse();
        assertThat(G5OptIn.scenarioEnabled("G5-3")).isFalse();
    }
}
