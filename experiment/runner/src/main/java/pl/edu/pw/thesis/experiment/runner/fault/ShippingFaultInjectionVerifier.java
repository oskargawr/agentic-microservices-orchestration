package pl.edu.pw.thesis.experiment.runner.fault;

/**
 * Fail-closed confirmation that the live Shipping process honors {@code X-Thesis-Fault} for F2.
 *
 * <p>Requires HTTP 409 with body code {@code ARRANGE_REJECTED}. A bare HTTP 409 is not sufficient
 * ({@code IDEMPOTENCY_KEY_CONFLICT} is also 409). Endpoint identity is not authenticated — a forged
 * base URL can produce a false positive.
 */
public interface ShippingFaultInjectionVerifier {

    PaymentInjectionCheckResult check();
}
