package pl.edu.pw.thesis.experiment.runner.fault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;

public final class HttpShippingArrangeTransport implements ShippingArrangeTransport {

    public static final String ARRANGE_PATH = "/api/v1/shipments/arrange";
    public static final String FAULT_HEADER = "X-Thesis-Fault";

    private final URI arrangeUri;
    private final HttpClient client;
    private final Duration timeout;

    public HttpShippingArrangeTransport(String shippingBaseUrl) {
        this(
                shippingBaseUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Duration.ofSeconds(10));
    }

    public HttpShippingArrangeTransport(String shippingBaseUrl, HttpClient client, Duration timeout) {
        Objects.requireNonNull(shippingBaseUrl, "shippingBaseUrl");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(timeout, "timeout");
        String base = shippingBaseUrl.endsWith("/")
                ? shippingBaseUrl.substring(0, shippingBaseUrl.length() - 1)
                : shippingBaseUrl;
        this.arrangeUri = URI.create(base + ARRANGE_PATH);
        this.client = client;
        this.timeout = timeout;
    }

    @Override
    public Response postArrange(String idempotencyKey, String faultHeader, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(arrangeUri)
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
