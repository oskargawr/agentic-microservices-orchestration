package pl.edu.pw.thesis.experiment.runner.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.HarnessStatus;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;

/**
 * File-first ResultStore (D-R2). Atomic temp+rename. Persist-fail after Oracle: retry without re-run.
 */
public final class FileResultStore {

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

    public void saveExperiment(ExperimentDefinition experiment) throws IOException {
        Files.createDirectories(experimentDir);
        atomicWriteJson(experimentDir.resolve("experiment.json"), experiment);
    }

    /**
     * Persists trial with retries. Does not re-execute the business trial.
     * Refuses overwrite of an existing terminal artifact with a different execution.
     */
    public void saveTrial(TrialRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        Path trialsDir = experimentDir.resolve("trials");
        Files.createDirectories(trialsDir);
        Path target = trialsDir.resolve(record.trialId() + ".json");

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
            }
        }

        IOException last = null;
        for (int attempt = 1; attempt <= maxPersistAttempts; attempt++) {
            try {
                atomicWriteJson(target, record);
                appendCsvRow(record);
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException(
                "PERSISTENCE_FAILURE after " + maxPersistAttempts + " attempts", last);
    }

    public boolean hasTerminalArtifact(String trialId) throws IOException {
        Path target = experimentDir.resolve("trials").resolve(trialId + ".json");
        if (!Files.exists(target)) {
            return false;
        }
        TrialRecord existing = mapper.readValue(target.toFile(), TrialRecord.class);
        return existing.harnessStatus() == HarnessStatus.COMPLETED
                || existing.harnessStatus() == HarnessStatus.EXCLUDED;
    }

    private void atomicWriteJson(Path target, Object value) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path partial = parent.resolve(target.getFileName().toString() + ".partial");
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        Files.write(
                partial,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.move(
                partial,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private void appendCsvRow(TrialRecord record) throws IOException {
        Path csv = experimentDir.resolve("trials.csv");
        boolean writeHeader = !Files.exists(csv) || Files.size(csv) == 0;
        List<String> lines = new ArrayList<>();
        if (writeHeader) {
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
        }
        lines.add(String.join(
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
                csv(record.gitSha())));
        Files.write(
                csv,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
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
