package pl.edu.pw.thesis.experiment.runner.fault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpPaymentFaultInjectionVerifierTest {

    @Test
    void enabled_whenChargeFailYieldsFailed() {
        PaymentChargeTransport transport = (key, fault, body) -> {
            assertThat(fault).isEqualTo("CHARGE_FAIL");
            assertThat(key).startsWith("f1p-preflight-");
            return new PaymentChargeTransport.Response(
                    200,
                    "{\"paymentId\":\"00000000-0000-4000-8000-000000000001\","
                            + "\"orderId\":\"00000000-0000-4000-8000-000000000002\","
                            + "\"amount\":{\"amountMinor\":1,\"currency\":\"PLN\"},"
                            + "\"state\":\"FAILED\"}");
        };
        PaymentInjectionCheckResult result =
                new HttpPaymentFaultInjectionVerifier(transport).check();
        assertThat(result.enabled()).isTrue();
        assertThat(result.detail()).contains("FAILED");
    }

    @Test
    void disabled_whenChargeFailYieldsCaptured() {
        PaymentChargeTransport transport = (key, fault, body) -> new PaymentChargeTransport.Response(
                200,
                "{\"paymentId\":\"00000000-0000-4000-8000-000000000001\","
                        + "\"orderId\":\"00000000-0000-4000-8000-000000000002\","
                        + "\"amount\":{\"amountMinor\":1,\"currency\":\"PLN\"},"
                        + "\"state\":\"CAPTURED\"}");
        PaymentInjectionCheckResult result =
                new HttpPaymentFaultInjectionVerifier(transport).check();
        assertThat(result.enabled()).isFalse();
        assertThat(result.detail()).contains("CAPTURED");
        assertThat(result.detail()).contains("fault-injection-enabled");
    }

    @Test
    void disabled_onNon2xx() {
        PaymentChargeTransport transport =
                (key, fault, body) -> new PaymentChargeTransport.Response(503, "unavailable");
        assertThat(new HttpPaymentFaultInjectionVerifier(transport).check().enabled()).isFalse();
    }

    @Test
    void disabled_onTransportError() {
        PaymentChargeTransport transport = (key, fault, body) -> {
            throw new java.io.IOException("connection refused");
        };
        PaymentInjectionCheckResult result =
                new HttpPaymentFaultInjectionVerifier(transport).check();
        assertThat(result.enabled()).isFalse();
        assertThat(result.detail()).contains("IOException");
    }
}
