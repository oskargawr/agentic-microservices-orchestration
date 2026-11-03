package pl.edu.pw.thesis.experiment.runner.fault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;

/** JDK HttpClient probes against Payment charge / resolve / GET. */
public final class HttpPaymentProbeTransport implements PaymentProbeTransport {

    public static final String CHARGE_PATH = "/api/v1/payments/charge";
    public static final String FAULT_HEADER = "X-Thesis-Fault";

    private final String base;
    private final HttpClient client;
    private final Duration timeout;

    public HttpPaymentProbeTransport(String paymentBaseUrl) {
        this(paymentBaseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Duration.ofSeconds(10));
    }

    public HttpPaymentProbeTransport(String paymentBaseUrl, HttpClient client, Duration timeout) {
        Objects.requireNonNull(paymentBaseUrl, "paymentBaseUrl");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(timeout, "timeout");
        this.base = paymentBaseUrl.endsWith("/")
                ? paymentBaseUrl.substring(0, paymentBaseUrl.length() - 1)
                : paymentBaseUrl;
        this.client = client;
        this.timeout = timeout;
    }

    @Override
    public Response postCharge(String idempotencyKey, String faultHeader, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + CHARGE_PATH))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(IdempotencyRules.HEADER, idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (faultHeader != null && !faultHeader.isBlank()) {
            builder.header(FAULT_HEADER, faultHeader.trim());
        }
        return send(builder.build());
    }

    @Override
    public Response postResolve(UUID paymentId, String faultHeader)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(base + "/api/v1/payments/" + paymentId + "/resolve"))
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (faultHeader != null && !faultHeader.isBlank()) {
            builder.header(FAULT_HEADER, faultHeader.trim());
        }
        return send(builder.build());
    }

    @Override
    public Response getPayment(UUID paymentId, String faultHeader)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(base + "/api/v1/payments/" + paymentId))
                .timeout(timeout)
                .GET();
        if (faultHeader != null && !faultHeader.isBlank()) {
            builder.header(FAULT_HEADER, faultHeader.trim());
        }
        return send(builder.build());
    }

    private Response send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body() == null ? "" : response.body());
    }
}
