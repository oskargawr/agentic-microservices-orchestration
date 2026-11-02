package pl.edu.pw.thesis.experiment.runner.fault;

/**
 * Fail-closed confirmation that the live Payment process honors {@code X-Thesis-Fault}.
 *
 * <p>A bare HTTP 2xx is <strong>not</strong> sufficient — the verifier must observe the
 * semantic effect of {@code CHARGE_FAIL} (persisted/observed {@code FAILED}).
 */
public interface PaymentFaultInjectionVerifier {

    PaymentInjectionCheckResult check();
}
