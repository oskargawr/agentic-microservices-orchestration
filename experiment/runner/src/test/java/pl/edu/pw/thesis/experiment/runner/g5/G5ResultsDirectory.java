package pl.edu.pw.thesis.experiment.runner.g5;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the operator-selected durable output directory for live G5 evidence.
 *
 * <p>Live G5 must never write its only evidence under JUnit's automatically cleaned
 * {@code @TempDir}. The operator must explicitly select an absolute path.
 */
final class G5ResultsDirectory {

    static final String ENV_RESULTS_DIR = "THESIS_G5_RESULTS_DIR";

    private G5ResultsDirectory() {}

    static Path resolve(String configuredPath) throws IOException {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException(
                    ENV_RESULTS_DIR + " is required for live G5; choose a durable absolute directory.");
        }

        Path path = Path.of(configuredPath.trim());
        if (!path.isAbsolute()) {
            throw new IllegalStateException(
                    ENV_RESULTS_DIR + " must be an absolute path; received: " + configuredPath);
        }

        Path normalized = path.normalize();
        Files.createDirectories(normalized);
        if (!Files.isDirectory(normalized) || !Files.isWritable(normalized)) {
            throw new IllegalStateException(
                    ENV_RESULTS_DIR + " must resolve to a writable directory: " + normalized);
        }
        return normalized;
    }
}
