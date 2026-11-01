package pl.edu.pw.thesis.contracts.oracle;

/**
 * Independent oracle labels (TH-011). Not derived from LLM text or Temporal status alone.
 */
public enum BusinessOutcome {
    CONFIRMED_SUCCESS,
    CONFIRMED_FAILURE,
    AMBIGUOUS,
    INVALID
}
