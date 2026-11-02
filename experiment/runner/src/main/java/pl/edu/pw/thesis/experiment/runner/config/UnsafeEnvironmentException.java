package pl.edu.pw.thesis.experiment.runner.config;

public final class UnsafeEnvironmentException extends RuntimeException {

    private final String exclusionReason;

    public UnsafeEnvironmentException(String exclusionReason, String message) {
        super(message);
        this.exclusionReason = exclusionReason;
    }

    public String exclusionReason() {
        return exclusionReason;
    }
}
