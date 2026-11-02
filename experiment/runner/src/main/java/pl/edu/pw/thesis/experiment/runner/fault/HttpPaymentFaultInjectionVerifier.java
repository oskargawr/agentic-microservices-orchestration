package pl.edu.pw.thesis.experiment.runner.fault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.payment.ChargeRequest;
import pl.edu.pw.thesis.contracts.payment.PaymentState;

/**
 * Proves {@code thesis.payment.fault-injection-enabled=true} by observing that an existing
 * {@code X-Thesis-Fault: CHARGE_FAIL} charge yields observed state {@link PaymentState#FAILED}.
 *
 * <p>When injection is off, Payment silently maps faults to {@code CAPTURED} — HTTP 2xx alone
 * therefore does <strong>not</strong> confirm the flag.
 */
public final class HttpPaymentFaultInjectionVerifier implements PaymentFaultInjectionVerifier {

    public static final String CODE_CHARGE_FAIL = "CHARGE_FAIL";

    private final PaymentChargeTransport transport;
    private final ObjectMapper mapper;

    public HttpPaymentFaultInjectionVerifier(PaymentChargeTransport transport) {
        this(transport, new ObjectMapper().findAndRegisterModules());
    }

    public HttpPaymentFaultInjectionVerifier(PaymentChargeTransport transport, ObjectMapper mapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public static HttpPaymentFaultInjectionVerifier forBaseUrl(String paymentBaseUrl) {
        return new HttpPaymentFaultInjectionVerifier(new HttpPaymentChargeTransport(paymentBaseUrl));
    }

    @Override
    public PaymentInjectionCheckResult check() {
        UUID probeOrderId = UUID.randomUUID();
        String idempotencyKey = "f1p-preflight-" + probeOrderId;
        try {
            String body = mapper.writeValueAsString(new ChargeRequest(probeOrderId, Money.plnMinor(1)));
            PaymentChargeTransport.Response response =
                    transport.postCharge(idempotencyKey, CODE_CHARGE_FAIL, body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "payment charge preflight HTTP " + response.statusCode()
                                + " (need 2xx with state=FAILED); body="
                                + truncate(response.body()));
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode stateNode = root.get("state");
            if (stateNode == null || stateNode.isNull() || stateNode.asText().isBlank()) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "payment charge preflight missing state field; body="
                                + truncate(response.body()));
            }
            String state = stateNode.asText();
            if (PaymentState.FAILED.name().equals(state)) {
                return PaymentInjectionCheckResult.enabled(
                        "CHARGE_FAIL produced FAILED (injection enabled); probeOrderId="
                                + probeOrderId);
            }
            if (PaymentState.CAPTURED.name().equals(state)) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "CHARGE_FAIL produced CAPTURED — thesis.payment.fault-injection-enabled "
                                + "appears false on the live Payment process; probeOrderId="
                                + probeOrderId);
            }
            return PaymentInjectionCheckResult.disabledOrUnknown(
                    "CHARGE_FAIL produced unexpected state=" + state
                            + " (required FAILED); probeOrderId=" + probeOrderId);
        } catch (Exception e) {
            return PaymentInjectionCheckResult.disabledOrUnknown(
                    "payment injection preflight failed: " + e.getClass().getSimpleName()
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
