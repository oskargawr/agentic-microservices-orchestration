package pl.edu.pw.thesis.experiment.runner.fault;

/**
 * Fail-closed confirmation of F3 payment injection (charge lost-response and optional opacity).
 */
public interface PaymentF3FaultInjectionVerifier {

    /** F3-Recovered: F3 charge observes UNKNOWN while DB/GET expose CAPTURED without opacity. */
    PaymentInjectionCheckResult checkRecovered();

    /**
     * F3-Unresolved: recovered checks plus resolve/GET with {@code RESOLVE_TRANSPORT_OPAQUE}
     * return HTTP 503 without defeating CAPTURED visibility when the header is absent.
     */
    PaymentInjectionCheckResult checkUnresolved();
}
