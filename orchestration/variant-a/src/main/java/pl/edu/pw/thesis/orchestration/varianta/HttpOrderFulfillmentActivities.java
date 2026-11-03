package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.failure.ApplicationFailure;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pl.edu.pw.thesis.contracts.common.IdempotencyKeys;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ConsumeResponse;
import pl.edu.pw.thesis.contracts.inventory.ReleaseResponse;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReserveRequest;
import pl.edu.pw.thesis.contracts.inventory.ReserveResponse;
import pl.edu.pw.thesis.contracts.payment.ChargeRequest;
import pl.edu.pw.thesis.contracts.payment.ChargeResponse;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.payment.RefundResponse;
import pl.edu.pw.thesis.contracts.shipping.ArrangeRequest;
import pl.edu.pw.thesis.contracts.shipping.ArrangeResponse;
import pl.edu.pw.thesis.contracts.shipping.CancelShipmentResponse;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;

/** Activity implementation that calls service HTTP APIs only (no direct DB access). */
public class HttpOrderFulfillmentActivities implements OrderFulfillmentActivities {

    public static final String FAULT_HEADER = "X-Thesis-Fault";

    private final RestClient inventory;
    private final RestClient payment;
    private final RestClient shipping;

    public HttpOrderFulfillmentActivities(ServiceEndpoints endpoints) {
        this.inventory = RestClient.builder().baseUrl(endpoints.inventoryBaseUrl()).build();
        this.payment = RestClient.builder().baseUrl(endpoints.paymentBaseUrl()).build();
        this.shipping = RestClient.builder().baseUrl(endpoints.shippingBaseUrl()).build();
    }

    @Override
    public ReserveResponse reserve(UUID orderId, String sku, int qty) {
        return post(
                inventory,
                "/api/v1/reservations",
                IdempotencyKeys.reserve(orderId),
                new ReserveRequest(orderId, sku, qty),
                null,
                ReserveResponse.class);
    }

    @Override
    public ChargeResponse charge(UUID orderId, Money amount, String faultHint) {
        return post(
                payment,
                "/api/v1/payments/charge",
                IdempotencyKeys.charge(orderId),
                new ChargeRequest(orderId, amount),
                faultHint,
                ChargeResponse.class);
    }

    @Override
    public ArrangeResponse arrange(UUID orderId, String faultHint) {
        return post(
                shipping,
                "/api/v1/shipments/arrange",
                IdempotencyKeys.arrange(orderId),
                new ArrangeRequest(orderId),
                faultHint,
                ArrangeResponse.class);
    }

    @Override
    public ConsumeResponse consume(UUID reservationId) {
        return post(
                inventory,
                "/api/v1/reservations/" + reservationId + "/consume",
                IdempotencyKeys.consume(reservationId),
                null,
                null,
                ConsumeResponse.class);
    }

    @Override
    public ReleaseResponse release(UUID reservationId) {
        return post(
                inventory,
                "/api/v1/reservations/" + reservationId + "/release",
                IdempotencyKeys.release(reservationId),
                null,
                null,
                ReleaseResponse.class);
    }

    @Override
    public RefundResponse refund(UUID paymentId) {
        return post(
                payment,
                "/api/v1/payments/" + paymentId + "/refund",
                IdempotencyKeys.refund(paymentId),
                null,
                null,
                RefundResponse.class);
    }

    @Override
    public CancelShipmentResponse cancelShipment(UUID shipmentId) {
        return post(
                shipping,
                "/api/v1/shipments/" + shipmentId + "/cancel",
                IdempotencyKeys.cancelShipment(shipmentId),
                null,
                null,
                CancelShipmentResponse.class);
    }

    @Override
    public PaymentSnapshot resolvePayment(UUID paymentId, String faultHint) {
        try {
            RestClient.RequestHeadersSpec<?> spec = payment.post()
                    .uri("/api/v1/payments/" + paymentId + "/resolve");
            if (faultHint != null && !faultHint.isBlank()) {
                spec = spec.header(FAULT_HEADER, faultHint.trim());
            }
            return spec.retrieve().body(PaymentSnapshot.class);
        } catch (RestClientResponseException e) {
            throw toFailure(e);
        }
    }

    @Override
    public ReservationSnapshot getReservation(UUID reservationId) {
        return get(inventory, "/api/v1/reservations/" + reservationId, ReservationSnapshot.class);
    }

    @Override
    public PaymentSnapshot getPayment(UUID paymentId, String faultHint) {
        try {
            RestClient.RequestHeadersSpec<?> spec = payment.get()
                    .uri("/api/v1/payments/" + paymentId);
            if (faultHint != null && !faultHint.isBlank()) {
                spec = spec.header(FAULT_HEADER, faultHint.trim());
            }
            return spec.retrieve().body(PaymentSnapshot.class);
        } catch (RestClientResponseException e) {
            throw toFailure(e);
        }
    }

    @Override
    public ShipmentSnapshot getShipment(UUID shipmentId) {
        return get(shipping, "/api/v1/shipments/" + shipmentId, ShipmentSnapshot.class);
    }

    private static <T> T get(RestClient client, String path, Class<T> type) {
        try {
            return client.get()
                    .uri(path)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException e) {
            throw toFailure(e);
        }
    }

    private static <T> T post(
            RestClient client,
            String path,
            String idempotencyKey,
            Object body,
            String faultHint,
            Class<T> type) {
        try {
            RestClient.RequestBodySpec spec = client.post()
                    .uri(path)
                    .header(IdempotencyRules.HEADER, idempotencyKey);
            if (faultHint != null && !faultHint.isBlank()) {
                spec = spec.header(FAULT_HEADER, faultHint.trim());
            }
            if (body != null) {
                return spec.contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(type);
            }
            return spec.retrieve().body(type);
        } catch (RestClientResponseException e) {
            throw toFailure(e);
        }
    }

    private static ApplicationFailure toFailure(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String type = (status >= 400 && status < 500)
                ? ActivityPolicies.BUSINESS_FAILURE_TYPE
                : "HTTP_" + status;
        if (status >= 400 && status < 500) {
            return ApplicationFailure.newNonRetryableFailure(
                    e.getResponseBodyAsString(), type);
        }
        return ApplicationFailure.newFailure(e.getResponseBodyAsString(), type);
    }
}
