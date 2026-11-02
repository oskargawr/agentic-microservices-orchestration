package pl.edu.pw.thesis.experiment.runner.fault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;

/** JDK HttpClient POST to existing {@code POST /api/v1/payments/charge}. */
public final class HttpPaymentChargeTransport implements PaymentChargeTransport {

    public static final String CHARGE_PATH = "/api/v1/payments/charge";
    public static final String FAULT_HEADER = "X-Thesis-Fault";

    private final URI chargeUri;
    private final HttpClient client;
    private final Duration timeout;

    public HttpPaymentChargeTransport(String paymentBaseUrl) {
        this(paymentBaseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Duration.ofSeconds(10));
    }

    public HttpPaymentChargeTransport(String paymentBaseUrl, HttpClient client, Duration timeout) {
        Objects.requireNonNull(paymentBaseUrl, "paymentBaseUrl");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(timeout, "timeout");
        String base = paymentBaseUrl.endsWith("/")
                ? paymentBaseUrl.substring(0, paymentBaseUrl.length() - 1)
                : paymentBaseUrl;
        this.chargeUri = URI.create(base + CHARGE_PATH);
        this.client = client;
        this.timeout = timeout;
    }

    @Override
    public Response postCharge(String idempotencyKey, String faultHeader, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(chargeUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(IdempotencyRules.HEADER, idempotencyKey)
                .header(FAULT_HEADER, faultHeader)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body() == null ? "" : response.body());
    }
}
