package pl.edu.pw.thesis.experiment.oracle.snapshot;

import java.time.Instant;
import java.util.UUID;

public record OperationLogEntry(
        String schema,
        long id,
        String operation,
        UUID resourceId,
        UUID orderId,
        String detail,
        Instant createdAt) {}
