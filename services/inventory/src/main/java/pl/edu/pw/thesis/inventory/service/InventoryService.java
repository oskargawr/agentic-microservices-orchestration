package pl.edu.pw.thesis.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pw.thesis.contracts.common.ErrorCode;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;
import pl.edu.pw.thesis.contracts.inventory.ConsumeResponse;
import pl.edu.pw.thesis.contracts.inventory.ReleaseResponse;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.inventory.ReserveRequest;
import pl.edu.pw.thesis.contracts.inventory.ReserveResponse;
import pl.edu.pw.thesis.contracts.inventory.StockSnapshot;
import pl.edu.pw.thesis.inventory.api.InventoryException;
import pl.edu.pw.thesis.inventory.persistence.IdempotencyRow;
import pl.edu.pw.thesis.inventory.persistence.InventoryRepository;
import pl.edu.pw.thesis.inventory.persistence.ReservationRow;
import pl.edu.pw.thesis.inventory.persistence.StockRow;

/**
 * Inventory business logic (TH-102).
 *
 * <p><strong>Concurrency / idempotency strategy:</strong>
 * Each mutating operation runs in one DB transaction. Order of locks:
 * (1) {@code idempotency_records} row {@code FOR UPDATE} if present — serialize same-key retries;
 * (2) {@code stock} row {@code FOR UPDATE} for reserve/release/consume — prevent oversell;
 * (3) {@code reservations} row {@code FOR UPDATE} for release/consume — single terminal transition.
 * Idempotency insert is unique on key; {@link DuplicateKeyException} retries as replay.
 * Business mutation + idempotency store + operation_log commit atomically.
 */
@Service
public class InventoryService {

    private final InventoryRepository repo;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ReservationSnapshot getReservation(UUID reservationId) {
        return repo.findReservation(reservationId)
                .map(r -> new ReservationSnapshot(
                        r.reservationId(), r.orderId(), r.sku(), r.qty(), r.state()))
                .orElseThrow(() -> new InventoryException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    @Transactional(readOnly = true)
    public StockSnapshot getStock(String sku) {
        return repo.findStock(sku)
                .map(s -> new StockSnapshot(s.sku(), s.onHand(), s.held()))
                .orElseThrow(() -> new InventoryException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "SKU not found"));
    }

    @Transactional
    public ReserveResponse reserve(String idempotencyKey, ReserveRequest request) {
        IdempotencyRules.requireKey(idempotencyKey);
        String fingerprint = request.idempotencyFingerprint();
        repo.advisoryLockKey(idempotencyKey);

        var existing = repo.findIdempotencyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint, ReserveResponse.class);
        }

        StockRow stock = repo.findStockForUpdate(request.sku())
                .orElseThrow(() -> new InventoryException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "SKU not found", idempotencyKey));

        if (stock.available() < request.qty()) {
            throw new InventoryException(
                    ErrorCode.INSUFFICIENT_STOCK,
                    HttpStatus.CONFLICT,
                    "Insufficient available stock",
                    idempotencyKey);
        }

        UUID reservationId = UUID.randomUUID();
        try {
            repo.insertReservation(new ReservationRow(
                    reservationId, request.orderId(), request.sku(), request.qty(), ReservationState.HELD));
        } catch (DuplicateKeyException e) {
            throw new InventoryException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    HttpStatus.CONFLICT,
                    "Order already has a HELD reservation",
                    idempotencyKey);
        }

        repo.updateStock(stock.sku(), stock.onHand(), stock.held() + request.qty());

        ReserveResponse response = new ReserveResponse(
                reservationId, request.orderId(), request.sku(), request.qty(), ReservationState.HELD);
        persistSuccess(idempotencyKey, "reserve", fingerprint, response);
        repo.appendOperationLog(
                "reserve", reservationId, request.orderId(), request.sku(), request.qty(),
                idempotencyKey, "HELD");
        return response;
    }

    @Transactional
    public ReleaseResponse release(String idempotencyKey, UUID reservationId) {
        IdempotencyRules.requireKey(idempotencyKey);
        String fingerprint = reservationId.toString();
        repo.advisoryLockKey(idempotencyKey);

        var existing = repo.findIdempotencyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint, ReleaseResponse.class);
        }

        ReservationRow reservation = repo.findReservationForUpdate(reservationId)
                .orElseThrow(() -> new InventoryException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Reservation not found", idempotencyKey));

        if (reservation.state() == ReservationState.RELEASED) {
            ReleaseResponse response = new ReleaseResponse(reservationId, ReservationState.RELEASED);
            persistSuccess(idempotencyKey, "release", fingerprint, response);
            return response;
        }
        if (reservation.state() != ReservationState.HELD) {
            throw new InventoryException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    HttpStatus.CONFLICT,
                    "Cannot release reservation in state " + reservation.state(),
                    idempotencyKey);
        }

        StockRow stock = repo.findStockForUpdate(reservation.sku())
                .orElseThrow(() -> new InventoryException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "SKU not found", idempotencyKey));
        int newHeld = stock.held() - reservation.qty();
        if (newHeld < 0) {
            throw new InventoryException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    HttpStatus.CONFLICT,
                    "Stock held inconsistent with reservation",
                    idempotencyKey);
        }
        repo.updateStock(stock.sku(), stock.onHand(), newHeld);
        repo.updateReservationState(reservationId, ReservationState.RELEASED);

        ReleaseResponse response = new ReleaseResponse(reservationId, ReservationState.RELEASED);
        persistSuccess(idempotencyKey, "release", fingerprint, response);
        repo.appendOperationLog(
                "release", reservationId, reservation.orderId(), reservation.sku(), reservation.qty(),
                idempotencyKey, "RELEASED");
        return response;
    }

    @Transactional
    public ConsumeResponse consume(String idempotencyKey, UUID reservationId) {
        IdempotencyRules.requireKey(idempotencyKey);
        String fingerprint = reservationId.toString();
        repo.advisoryLockKey(idempotencyKey);

        var existing = repo.findIdempotencyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint, ConsumeResponse.class);
        }

        ReservationRow reservation = repo.findReservationForUpdate(reservationId)
                .orElseThrow(() -> new InventoryException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Reservation not found", idempotencyKey));

        if (reservation.state() == ReservationState.CONSUMED) {
            ConsumeResponse response = new ConsumeResponse(reservationId, ReservationState.CONSUMED);
            persistSuccess(idempotencyKey, "consume", fingerprint, response);
            return response;
        }
        if (reservation.state() != ReservationState.HELD) {
            throw new InventoryException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    HttpStatus.CONFLICT,
                    "Cannot consume reservation in state " + reservation.state(),
                    idempotencyKey);
        }

        StockRow stock = repo.findStockForUpdate(reservation.sku())
                .orElseThrow(() -> new InventoryException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "SKU not found", idempotencyKey));
        int newHeld = stock.held() - reservation.qty();
        int newOnHand = stock.onHand() - reservation.qty();
        if (newHeld < 0 || newOnHand < 0 || newOnHand < newHeld) {
            throw new InventoryException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    HttpStatus.CONFLICT,
                    "Stock accounting would violate invariants",
                    idempotencyKey);
        }
        repo.updateStock(stock.sku(), newOnHand, newHeld);
        repo.updateReservationState(reservationId, ReservationState.CONSUMED);

        ConsumeResponse response = new ConsumeResponse(reservationId, ReservationState.CONSUMED);
        persistSuccess(idempotencyKey, "consume", fingerprint, response);
        repo.appendOperationLog(
                "consume", reservationId, reservation.orderId(), reservation.sku(), reservation.qty(),
                idempotencyKey, "CONSUMED");
        return response;
    }

    private void persistSuccess(String key, String operation, String fingerprint, Object response) {
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
