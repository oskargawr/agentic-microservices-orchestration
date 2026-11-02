package pl.edu.pw.thesis.experiment.runner.fault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.shipping.ArrangeRequest;

/**
 * Proves shipping F2 injection by observing HTTP 409 with {@code code=ARRANGE_REJECTED}.
 */
public final class HttpShippingFaultInjectionVerifier implements ShippingFaultInjectionVerifier {

    public static final String CODE_ARRANGE_REJECTED = "ARRANGE_REJECTED";

    private final ShippingArrangeTransport transport;
    private final ObjectMapper mapper;

    public HttpShippingFaultInjectionVerifier(ShippingArrangeTransport transport) {
        this(transport, new ObjectMapper().findAndRegisterModules());
    }

    public HttpShippingFaultInjectionVerifier(ShippingArrangeTransport transport, ObjectMapper mapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public static HttpShippingFaultInjectionVerifier forBaseUrl(String shippingBaseUrl) {
        return new HttpShippingFaultInjectionVerifier(new HttpShippingArrangeTransport(shippingBaseUrl));
    }

    @Override
    public PaymentInjectionCheckResult check() {
        UUID probeOrderId = UUID.randomUUID();
        String idempotencyKey = "f2-preflight-" + probeOrderId;
        try {
            String body = mapper.writeValueAsString(new ArrangeRequest(probeOrderId));
            ShippingArrangeTransport.Response response =
                    transport.postArrange(idempotencyKey, CODE_ARRANGE_REJECTED, body);
            if (response.statusCode() != 409) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "shipping arrange preflight HTTP " + response.statusCode()
                                + " (need 409 with code=ARRANGE_REJECTED); body="
                                + truncate(response.body()));
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode codeNode = root.get("code");
            if (codeNode == null || codeNode.isNull() || codeNode.asText().isBlank()) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "shipping arrange preflight 409 missing code; body="
                                + truncate(response.body()));
            }
            String code = codeNode.asText();
            if (CODE_ARRANGE_REJECTED.equals(code)) {
                return PaymentInjectionCheckResult.enabled(
                        "ARRANGE_REJECTED produced HTTP 409 (injection enabled); probeOrderId="
                                + probeOrderId);
            }
            return PaymentInjectionCheckResult.disabledOrUnknown(
                    "shipping arrange preflight 409 with unexpected code=" + code
                            + " (required ARRANGE_REJECTED); probeOrderId=" + probeOrderId);
        } catch (Exception e) {
            return PaymentInjectionCheckResult.disabledOrUnknown(
                    "shipping injection preflight failed: " + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
