package pl.edu.pw.thesis.orchestration.varianta.runtime;

import java.util.Objects;

/** Connection settings for a real Temporal server (not TestWorkflowEnvironment). */
public record TemporalSettings(String target, String namespace) {

    public static final String DEFAULT_TARGET = "localhost:7233";
    public static final String DEFAULT_NAMESPACE = "default";

    public TemporalSettings {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(namespace, "namespace");
        if (target.isBlank()) {
            throw new IllegalArgumentException("temporal target must not be blank");
        }
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("temporal namespace must not be blank");
        }
    }

    public static TemporalSettings fromEnvironment() {
        return new TemporalSettings(
                envOrDefault("TEMPORAL_TARGET", DEFAULT_TARGET),
                envOrDefault("TEMPORAL_NAMESPACE", DEFAULT_NAMESPACE));
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }
}
