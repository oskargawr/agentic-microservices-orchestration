package pl.edu.pw.thesis.experiment.runner.g5;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/**
 * Writes {@code g5-3-acceptance.json} without mutating any {@link TrialRecord}.
 */
public final class G5AcceptanceWriter {

    public static final String DEFAULT_FILE_NAME = "g5-3-acceptance.json";

    private final ObjectMapper mapper;

    public G5AcceptanceWriter() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path write(Path experimentResultsDir, G5AcceptanceRecord record) throws IOException {
        Objects.requireNonNull(experimentResultsDir, "experimentResultsDir");
        Objects.requireNonNull(record, "record");
        Path dir = experimentResultsDir.resolve("acceptance");
        Files.createDirectories(dir);
        Path target = dir.resolve(DEFAULT_FILE_NAME);
        mapper.writeValue(target.toFile(), record);
        return target;
    }

    public G5AcceptanceRecord read(Path acceptanceJson) throws IOException {
        return mapper.readValue(acceptanceJson.toFile(), G5AcceptanceRecord.class);
    }

    /**
     * Documents the immutability contract: acceptance evaluation must not alter trial fields.
     * This method intentionally performs no mutation and returns the same instance.
     */
    public static TrialRecord assertUnchanged(TrialRecord before, TrialRecord after) {
        if (before != after && !Objects.equals(before, after)) {
            throw new IllegalStateException(
                    "TrialRecord must remain immutable across G5-3 acceptance evaluation");
        }
        return after;
    }
}
