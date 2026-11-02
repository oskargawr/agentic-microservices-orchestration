package pl.edu.pw.thesis.experiment.runner.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentStatusRecord;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/**
 * File-first ResultStore (D-R2). JSON is canonical SoT; CSV is rebuilt from JSON (OWN-P / F).
 * Atomic temp+rename. Persist-fail after Oracle: partial + EXCLUDED/PERSISTENCE_FAILURE (OWN-P1).
 */
public class FileResultStore {

    private final Path experimentDir;
    private final ObjectMapper mapper;
    private final int maxPersistAttempts;

    public FileResultStore(Path resultsRoot, ExperimentDefinition experiment) {
        this(resultsRoot, experiment, 3);
    }

    public FileResultStore(Path resultsRoot, ExperimentDefinition experiment, int maxPersistAttempts) {
        Objects.requireNonNull(resultsRoot, "resultsRoot");
        Objects.requireNonNull(experiment, "experiment");
        this.experimentDir = resultsRoot
                .resolve(experiment.phase())
                .resolve(experiment.experimentId());
        this.maxPersistAttempts = Math.max(1, maxPersistAttempts);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path experimentDir() {
        return experimentDir;
    }

    public Path trialJsonPath(String trialId) {
        return experimentDir.resolve("trials").resolve(trialId + ".json");
    }

    public Path trialPartialPath(String trialId) {
        return experimentDir.resolve("trials").resolve(trialId + ".json.partial");
    }

    public void saveExperiment(ExperimentDefinition experiment) throws IOException {
        Files.createDirectories(experimentDir);
        atomicWriteJson(experimentDir.resolve("experiment.json"), experiment);
    }

    /** Experiment-level status (OWN-P3) — separate from trial JSON. */
    public void saveExperimentStatus(ExperimentStatusRecord status) throws IOException {
        Files.createDirectories(experimentDir);
        atomicWriteJson(experimentDir.resolve("experiment-status.json"), status);
    }

    public ExperimentStatusRecord loadExperimentStatus() throws IOException {
        Path path = experimentDir.resolve("experiment-status.json");
        if (!Files.exists(path)) {
            return null;
        }
        return mapper.readValue(path.toFile(), ExperimentStatusRecord.class);
    }

    /**
     * Writes Oracle-confirmed candidate to {@code .json.partial} without claiming COMPLETED.
     * Does not mutate an existing terminal {@code .json}.
     */
    public void saveOracleConfirmedPartial(TrialRecord candidate) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        Path trialsDir = experimentDir.resolve("trials");
        Files.createDirectories(trialsDir);
        Path partial = trialPartialPath(candidate.trialId());
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(candidate);
        Files.write(
                partial,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * Persists trial with retries. Does not re-execute the business trial.
     * Refuses overwrite of an existing terminal artifact with a different execution.
     * CSV is rebuilt from all durable trial JSON files (idempotent).
     */
    public void saveTrial(TrialRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        Path trialsDir = experimentDir.resolve("trials");
        Files.createDirectories(trialsDir);
        Path target = trialJsonPath(record.trialId());

        if (Files.exists(target)) {
            TrialRecord existing = mapper.readValue(target.toFile(), TrialRecord.class);
            if (existing.harnessStatus() == HarnessStatus.COMPLETED
                    || existing.harnessStatus() == HarnessStatus.EXCLUDED) {
                if (!Objects.equals(existing.orderId(), record.orderId())
                        || !Objects.equals(existing.workflowId(), record.workflowId())) {
                    throw new IOException(
                            "Refuse overwrite of terminal trial artifact trialId="
                                    + record.trialId());
                }
                // Never rewrite COMPLETED/EXCLUDED to ABORTED_EXPERIMENT via trial file
                if (record.harnessStatus() == HarnessStatus.ABORTED_EXPERIMENT) {
                    throw new IOException(
                            "Refuse rewrite of terminal trial as ABORTED_EXPERIMENT trialId="
                                    + record.trialId());
                }
            }
        }

        IOException last = null;
        for (int attempt = 1; attempt <= maxPersistAttempts; attempt++) {
            try {
                atomicWriteJson(target, record);
                rebuildCsvFromJsonTrials();
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException(
                "PERSISTENCE_FAILURE after " + maxPersistAttempts + " attempts", last);
    }

    public boolean hasTerminalArtifact(String trialId) throws IOException {
        Path target = trialJsonPath(trialId);
        if (!Files.exists(target)) {
            return false;
        }
        TrialRecord existing = mapper.readValue(target.toFile(), TrialRecord.class);
        return existing.harnessStatus() == HarnessStatus.COMPLETED
                || existing.harnessStatus() == HarnessStatus.EXCLUDED;
    }

    /** Rebuilds {@code trials.csv} from durable {@code trials/*.json} only (JSON SoT). */
    public void rebuildCsvFromJsonTrials() throws IOException {
        Path trialsDir = experimentDir.resolve("trials");
        Files.createDirectories(trialsDir);
        List<TrialRecord> records = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(trialsDir, "*.json")) {
            for (Path json : stream) {
                String name = json.getFileName().toString();
                if (name.endsWith(".partial")) {
                    continue;
                }
                records.add(mapper.readValue(json.toFile(), TrialRecord.class));
            }
        }
        records.sort(Comparator.comparing(TrialRecord::trialId));

        Path csv = experimentDir.resolve("trials.csv");
        Path partialCsv = experimentDir.resolve("trials.csv.partial");
        List<String> lines = new ArrayList<>();
        lines.add(String.join(
                ",",
                "experimentId",
                "trialId",
                "variant",
                "fault",
                "repetition",
                "harnessStatus",
                "includeInRqRates",
                "exclusionReason",
                "oracle_classification",
                "oracle_pattern",
                "terminatedBy",
                "workflowQuiesced",
                "orderId",
                "gitSha"));
        for (TrialRecord record : records) {
            lines.add(csvLine(record));
        }
        Files.write(
                partialCsv,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.move(
                partialCsv,
                csv,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private void atomicWriteJson(Path target, Object value) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        // Durable writes use *.writing; OWN-P1 candidate uses dedicated *.json.partial path.
        Path writing = parent.resolve(target.getFileName().toString() + ".writing");
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        Files.write(
                writing,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.move(
                writing,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static String csvLine(TrialRecord record) {
        return String.join(
                ",",
                csv(record.experimentId()),
                csv(record.trialId()),
                csv(record.variant()),
                csv(record.faultProfileId()),
                Integer.toString(record.repetitionIndex()),
                csv(record.harnessStatus() == null ? "" : record.harnessStatus().name()),
                Boolean.toString(record.includeInRqRates()),
                csv(record.exclusionReason()),
                csv(record.oracle() == null ? "" : record.oracle().classification()),
                csv(record.oracle() == null ? "" : record.oracle().pattern()),
                csv(record.terminatedBy() == null ? "" : record.terminatedBy().name()),
                Boolean.toString(record.workflowQuiesced()),
                csv(record.orderId() == null ? "" : record.orderId().toString()),
                csv(record.gitSha()));
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
