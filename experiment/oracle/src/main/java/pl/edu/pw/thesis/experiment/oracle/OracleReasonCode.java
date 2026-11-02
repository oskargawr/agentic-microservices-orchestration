package pl.edu.pw.thesis.experiment.oracle;

/**
 * Non-pattern diagnostic codes. Not oracle classification labels.
 */
public enum OracleReasonCode {
    NONE,
    COMPENSATION_INCOMPLETE,
    PARTIAL_SUCCESS_PACKAGE,
    UNKNOWN_PAYMENT,
    UNRESOLVED_PENDING_PAYMENT,
    MULTIPLE_RESERVATIONS,
    OPERATION_LOG_ENTITY_MISMATCH,
    OTHER_PARTIAL
}
