package pl.edu.pw.thesis.shipping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pw.thesis.contracts.common.ErrorCode;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;
import pl.edu.pw.thesis.contracts.shipping.ArrangeRequest;
import pl.edu.pw.thesis.contracts.shipping.ArrangeResponse;
import pl.edu.pw.thesis.contracts.shipping.CancelShipmentResponse;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.contracts.transition.ShipmentTransitions;
import pl.edu.pw.thesis.shipping.api.ShippingException;
import pl.edu.pw.thesis.shipping.persistence.IdempotencyRow;
import pl.edu.pw.thesis.shipping.persistence.ShippingRepository;
import pl.edu.pw.thesis.shipping.persistence.ShipmentRow;

/**
 * Shipping business logic (TH-104).
 *
 * <p>Concurrency: advisory lock on idempotency key; shipment row {@code FOR UPDATE} on cancel;
 * unique {@code order_id} prevents duplicate arrange. Mutation + idempotency + operation_log
 * commit in one transaction.
 */
@Service
public class ShippingService {

    private final ShippingRepository repo;
    private final ObjectMapper objectMapper;

    public ShippingService(ShippingRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ShipmentSnapshot getShipment(UUID shipmentId) {
        return toSnapshot(requireShipment(shipmentId));
    }

    @Transactional
    public ArrangeResponse arrange(String idempotencyKey, ArrangeRequest request) {
        IdempotencyRules.requireKey(idempotencyKey);
        String fingerprint = request.idempotencyFingerprint();
        repo.advisoryLockKey(idempotencyKey);

        var existing = repo.findIdempotencyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint, ArrangeResponse.class);
        }

        UUID shipmentId = UUID.randomUUID();
        ShipmentRow row = new ShipmentRow(shipmentId, request.orderId(), ShipmentState.ARRANGED);
        try {
            repo.insert(row);
        } catch (DuplicateKeyException e) {
            throw new ShippingException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    HttpStatus.CONFLICT,
                    "Shipment already exists for order",
                    idempotencyKey);
        }

        ArrangeResponse response = new ArrangeResponse(shipmentId, request.orderId(), ShipmentState.ARRANGED);
        persistIdempotency(idempotencyKey, "arrange", fingerprint, response);
        repo.appendOperationLog(
                "arrange", shipmentId, request.orderId(), idempotencyKey,
                "{\"state\":\"ARRANGED\"}");
        return response;
    }

    @Transactional
    public CancelShipmentResponse cancel(String idempotencyKey, UUID shipmentId) {
        IdempotencyRules.requireKey(idempotencyKey);
        String fingerprint = shipmentId.toString();
        repo.advisoryLockKey(idempotencyKey);

        var existing = repo.findIdempotencyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint, CancelShipmentResponse.class);
        }

        ShipmentRow shipment = repo.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new ShippingException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Shipment not found", idempotencyKey));

        if (shipment.state() == ShipmentState.CANCELLED) {
            CancelShipmentResponse response =
                    new CancelShipmentResponse(shipmentId, ShipmentState.CANCELLED);
            persistIdempotency(idempotencyKey, "cancel", fingerprint, response);
            return response;
        }

        ShipmentState next = ShipmentTransitions.cancel(shipment.state());
        if (next != shipment.state()) {
            repo.updateState(shipmentId, next);
        }

        CancelShipmentResponse response = new CancelShipmentResponse(shipmentId, next);
        persistIdempotency(idempotencyKey, "cancel", fingerprint, response);
        repo.appendOperationLog(
                "cancel", shipmentId, shipment.orderId(), idempotencyKey,
                "{\"from\":\"" + shipment.state() + "\",\"to\":\"" + next + "\"}");
        return response;
    }

    private ShipmentRow requireShipment(UUID shipmentId) {
        return repo.findById(shipmentId)
                .orElseThrow(() -> new ShippingException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Shipment not found"));
    }

    private ShipmentSnapshot toSnapshot(ShipmentRow row) {
        return new ShipmentSnapshot(row.shipmentId(), row.orderId(), row.state());
    }

    private void persistIdempotency(String key, String operation, String fingerprint, Object response) {
        repo.insertIdempotency(new IdempotencyRow(
                key, operation, fingerprint, HttpStatus.OK.value(), writeJson(response)));
    }

    private <T> T replay(IdempotencyRow row, String fingerprint, Class<T> type) {
        IdempotencyRules.assertPayloadCompatible(
                row.idempotencyKey(), row.requestFingerprint(), fingerprint);
        try {
            return objectMapper.readValue(row.responseBody(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt idempotency payload for " + row.idempotencyKey(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
