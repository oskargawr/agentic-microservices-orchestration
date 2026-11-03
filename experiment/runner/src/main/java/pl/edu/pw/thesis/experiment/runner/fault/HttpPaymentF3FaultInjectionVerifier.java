package pl.edu.pw.thesis.experiment.runner.fault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.payment.ChargeRequest;
import pl.edu.pw.thesis.contracts.payment.PaymentState;

/** Live Payment probes for F3-Recovered / F3-Unresolved fail-closed preflight. */
public final class HttpPaymentF3FaultInjectionVerifier implements PaymentF3FaultInjectionVerifier {

    public static final String CODE_F3 = "F3_CAPTURE_LOST_RESPONSE";
    public static final String CODE_OPAQUE = "RESOLVE_TRANSPORT_OPAQUE";

    private final PaymentProbeTransport transport;
    private final ObjectMapper mapper;

    public HttpPaymentF3FaultInjectionVerifier(PaymentProbeTransport transport) {
        this(transport, new ObjectMapper().findAndRegisterModules());
    }

    public HttpPaymentF3FaultInjectionVerifier(PaymentProbeTransport transport, ObjectMapper mapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public static HttpPaymentF3FaultInjectionVerifier forBaseUrl(String paymentBaseUrl) {
        return new HttpPaymentF3FaultInjectionVerifier(new HttpPaymentProbeTransport(paymentBaseUrl));
    }

    @Override
    public PaymentInjectionCheckResult checkRecovered() {
        try {
            F3ChargeProbe probe = chargeF3();
            if (probe.failureDetail() != null) {
                return PaymentInjectionCheckResult.disabledOrUnknown(probe.failureDetail());
            }
            PaymentProbeTransport.Response get =
                    transport.getPayment(probe.paymentId(), null);
            if (get.statusCode() < 200 || get.statusCode() >= 300) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "F3 recovered preflight GET HTTP " + get.statusCode()
                                + "; body=" + truncate(get.body()));
            }
            String state = readState(get.body());
            if (!PaymentState.CAPTURED.name().equals(state)) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "F3 charge observed UNKNOWN but unhinted GET state=" + state
                                + " (required CAPTURED); paymentId=" + probe.paymentId());
            }
            return PaymentInjectionCheckResult.enabled(
                    "F3 charge→UNKNOWN observe + unhinted GET CAPTURED; paymentId="
                            + probe.paymentId());
        } catch (Exception e) {
            return PaymentInjectionCheckResult.disabledOrUnknown(
                    "F3 recovered preflight failed: " + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }
    }

    @Override
    public PaymentInjectionCheckResult checkUnresolved() {
        PaymentInjectionCheckResult recovered = checkRecovered();
        if (!recovered.enabled()) {
            return recovered;
        }
        try {
            F3ChargeProbe probe = chargeF3();
            if (probe.failureDetail() != null) {
                return PaymentInjectionCheckResult.disabledOrUnknown(probe.failureDetail());
            }
            PaymentProbeTransport.Response resolve =
                    transport.postResolve(probe.paymentId(), CODE_OPAQUE);
            if (resolve.statusCode() != 503) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "RESOLVE_TRANSPORT_OPAQUE resolve expected HTTP 503, got "
                                + resolve.statusCode() + "; body=" + truncate(resolve.body()));
            }
            PaymentProbeTransport.Response opaqueGet =
                    transport.getPayment(probe.paymentId(), CODE_OPAQUE);
            if (opaqueGet.statusCode() != 503) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "RESOLVE_TRANSPORT_OPAQUE GET expected HTTP 503, got "
                                + opaqueGet.statusCode() + "; body=" + truncate(opaqueGet.body()));
            }
            PaymentProbeTransport.Response clearGet =
                    transport.getPayment(probe.paymentId(), null);
            if (clearGet.statusCode() < 200 || clearGet.statusCode() >= 300
                    || !PaymentState.CAPTURED.name().equals(readState(clearGet.body()))) {
                return PaymentInjectionCheckResult.disabledOrUnknown(
                        "after opacity, unhinted GET must expose CAPTURED; HTTP "
                                + clearGet.statusCode() + " body=" + truncate(clearGet.body()));
            }
            return PaymentInjectionCheckResult.enabled(
                    "F3 Unresolved opacity 503 on resolve/GET + CAPTURED without header; paymentId="
                            + probe.paymentId());
        } catch (Exception e) {
            return PaymentInjectionCheckResult.disabledOrUnknown(
                    "F3 unresolved preflight failed: " + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }
    }

    private F3ChargeProbe chargeF3() throws Exception {
        UUID probeOrderId = UUID.randomUUID();
        String idempotencyKey = "f3-preflight-" + probeOrderId;
        String body = mapper.writeValueAsString(new ChargeRequest(probeOrderId, Money.plnMinor(1)));
        PaymentProbeTransport.Response response =
                transport.postCharge(idempotencyKey, CODE_F3, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return F3ChargeProbe.fail(
                    "F3 charge preflight HTTP " + response.statusCode()
                            + "; body=" + truncate(response.body()));
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode stateNode = root.get("state");
        JsonNode idNode = root.get("paymentId");
        if (stateNode == null || idNode == null) {
            return F3ChargeProbe.fail(
                    "F3 charge preflight missing state/paymentId; body="
                            + truncate(response.body()));
        }
        if (!PaymentState.UNKNOWN.name().equals(stateNode.asText())) {
            if (PaymentState.CAPTURED.name().equals(stateNode.asText())) {
                return F3ChargeProbe.fail(
                        "F3_CAPTURE_LOST_RESPONSE produced CAPTURED observe — fault injection "
                                + "appears disabled; probeOrderId=" + probeOrderId);
            }
            return F3ChargeProbe.fail(
                    "F3 charge unexpected observe state=" + stateNode.asText()
                            + " (required UNKNOWN); probeOrderId=" + probeOrderId);
        }
        return F3ChargeProbe.ok(UUID.fromString(idNode.asText()));
    }

    private String readState(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode stateNode = root.get("state");
        return stateNode == null || stateNode.isNull() ? null : stateNode.asText();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    private record F3ChargeProbe(UUID paymentId, String failureDetail) {
        static F3ChargeProbe ok(UUID paymentId) {
            return new F3ChargeProbe(paymentId, null);
        }

        static F3ChargeProbe fail(String detail) {
            return new F3ChargeProbe(null, detail);
        }
    }
}
