package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class G5ResultsDirectoryTest {

    @TempDir
    Path tempDir;

    @Test
    void missingPathIsRejected() {
        assertThatThrownBy(() -> G5ResultsDirectory.resolve(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(G5ResultsDirectory.ENV_RESULTS_DIR);
        assertThatThrownBy(() -> G5ResultsDirectory.resolve("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(G5ResultsDirectory.ENV_RESULTS_DIR);
    }

    @Test
    void relativePathIsRejected() {
        assertThatThrownBy(() -> G5ResultsDirectory.resolve("build/g5-results"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absolute path");
    }

    @Test
    void absolutePathIsCreatedAndReturnedNormalized() throws Exception {
        Path requested = tempDir.resolve("nested").resolve("g5-results");
        Path resolved = G5ResultsDirectory.resolve(requested.toString());

        assertThat(resolved).isEqualTo(requested.normalize());
        assertThat(Files.isDirectory(resolved)).isTrue();
        assertThat(Files.isWritable(resolved)).isTrue();
    }
}
