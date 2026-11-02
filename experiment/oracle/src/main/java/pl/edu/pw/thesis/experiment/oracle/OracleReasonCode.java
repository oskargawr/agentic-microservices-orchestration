package pl.edu.pw.thesis.experiment.oracle;

/**
 * Non-pattern diagnostic codes. Not oracle classification labels.
 */
public enum OracleReasonCode {
    NONE,
    COMPENSATION_INCOMPLETE,
    PARTIAL_SUCCESS_PACKAGE,
    /** TH-011 §2.1 stock evidence absent — cannot confirm SUCCESS (not an I1 numeric violation). */
    MISSING_STOCK_EVIDENCE,
    UNKNOWN_PAYMENT,
    UNRESOLVED_PENDING_PAYMENT,
    MULTIPLE_RESERVATIONS,
    OPERATION_LOG_ENTITY_MISMATCH,
    OTHER_PARTIAL
}
