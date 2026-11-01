package pl.edu.pw.thesis.orchestration.varianta;

import java.util.Objects;

/** Base URLs for the three shared business services. */
public record ServiceEndpoints(
        String inventoryBaseUrl,
        String paymentBaseUrl,
        String shippingBaseUrl
) {
    public ServiceEndpoints {
        Objects.requireNonNull(inventoryBaseUrl, "inventoryBaseUrl");
        Objects.requireNonNull(paymentBaseUrl, "paymentBaseUrl");
        Objects.requireNonNull(shippingBaseUrl, "shippingBaseUrl");
    }

    public static ServiceEndpoints localhostDefaults() {
        return new ServiceEndpoints(
                "http://localhost:8081",
                "http://localhost:8082",
                "http://localhost:8083");
    }
}
