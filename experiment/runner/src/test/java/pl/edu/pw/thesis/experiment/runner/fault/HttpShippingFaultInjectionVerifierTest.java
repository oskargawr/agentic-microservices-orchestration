package pl.edu.pw.thesis.experiment.runner.fault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpShippingFaultInjectionVerifierTest {

    @Test
    void enabled_whenArrangeRejectedYields409WithCode() {
        ShippingArrangeTransport transport = (key, fault, body) -> {
            assertThat(fault).isEqualTo("ARRANGE_REJECTED");
            assertThat(key).startsWith("f2-preflight-");
            return new ShippingArrangeTransport.Response(
                    409,
                    "{\"code\":\"ARRANGE_REJECTED\",\"message\":\"rejected\",\"idempotencyKey\":\""
                            + key + "\"}");
        };
        PaymentInjectionCheckResult result =
                new HttpShippingFaultInjectionVerifier(transport).check();
        assertThat(result.enabled()).isTrue();
        assertThat(result.detail()).contains("ARRANGE_REJECTED");
    }

    @Test
    void disabled_when409IsIdempotencyConflict() {
        ShippingArrangeTransport transport = (key, fault, body) -> new ShippingArrangeTransport.Response(
                409,
                "{\"code\":\"IDEMPOTENCY_KEY_CONFLICT\",\"message\":\"conflict\"}");
        PaymentInjectionCheckResult result =
                new HttpShippingFaultInjectionVerifier(transport).check();
        assertThat(result.enabled()).isFalse();
        assertThat(result.detail()).contains("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void disabled_onNon409() {
        ShippingArrangeTransport transport =
                (key, fault, body) -> new ShippingArrangeTransport.Response(200, "{\"state\":\"ARRANGED\"}");
        assertThat(new HttpShippingFaultInjectionVerifier(transport).check().enabled()).isFalse();
    }

    @Test
    void disabled_onTransportError() {
        ShippingArrangeTransport transport = (key, fault, body) -> {
            throw new java.io.IOException("connection refused");
        };
        PaymentInjectionCheckResult result =
                new HttpShippingFaultInjectionVerifier(transport).check();
        assertThat(result.enabled()).isFalse();
        assertThat(result.detail()).contains("IOException");
    }
}
