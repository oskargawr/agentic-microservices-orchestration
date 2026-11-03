package pl.edu.pw.thesis.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pw.thesis.contracts.common.ErrorCode;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;
import pl.edu.pw.thesis.contracts.payment.ChargeRequest;
import pl.edu.pw.thesis.contracts.payment.ChargeResponse;
import pl.edu.pw.thesis.contracts.payment.PaymentResolveRules;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.payment.RefundResponse;
import pl.edu.pw.thesis.contracts.payment.ResolveProbeOutcome;
import pl.edu.pw.thesis.contracts.transition.PaymentTransitions;
import pl.edu.pw.thesis.payment.api.PaymentException;
import pl.edu.pw.thesis.payment.config.PaymentProperties;
import pl.edu.pw.thesis.payment.fault.ThesisFault;
import pl.edu.pw.thesis.payment.persistence.IdempotencyRow;
import pl.edu.pw.thesis.payment.persistence.PaymentRepository;
import pl.edu.pw.thesis.payment.persistence.PaymentRow;

/**
 * Payment business logic (TH-103).
 *
 * <p>Concurrency: advisory lock on idempotency key (charge/refund) or payment id (resolve);
 * {@code payments} row {@code FOR UPDATE}; unique {@code order_id}.
 * Idempotency + payment row + operation_log commit in one transaction.
 *
 * <p>F3: with fault injection enabled and {@link ThesisFault#F3_CAPTURE_LOST_RESPONSE},
 * persist {@link PaymentState#CAPTURED} but return {@link PaymentState#UNKNOWN} on charge.
 */
@Service
public class PaymentService {

    private final PaymentRepository repo;
    private final ObjectMapper objectMapper;
    private final PaymentProperties properties;

    public PaymentService(
            PaymentRepository repo, ObjectMapper objectMapper, PaymentProperties properties) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PaymentSnapshot getPayment(UUID paymentId) {
        return getPayment(paymentId, null);
    }

    /**
     * Read payment. When fault injection is enabled and {@code faultHeader} is
     * {@link ThesisFault#RESOLVE_TRANSPORT_OPAQUE}, returns HTTP 503 without reading a
     * mutating path — caller must not observe CAPTURED over this channel (F3-Unresolved).
     */
    @Transactional(readOnly = true)
    public PaymentSnapshot getPayment(UUID paymentId, String faultHeader) {
        assertNoTransportOpacity(faultHeader, "GET");
        return toSnapshot(requirePayment(paymentId));
    }

    @Transactional
    public ChargeResponse charge(String idempotencyKey, ChargeRequest request, String faultHeader) {
        IdempotencyRules.requireKey(idempotencyKey);
        String fingerprint = request.idempotencyFingerprint();
        repo.advisoryLockKey(idempotencyKey);

        var existing = repo.findIdempotencyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint, ChargeResponse.class);
        }

        ThesisFault fault = resolveChargeFault(faultHeader);
        PaymentState persisted = switch (fault) {
            case CHARGE_FAIL -> PaymentState.FAILED;
            case CHARGE_UNKNOWN -> PaymentState.UNKNOWN;
            case CHARGE_CAPTURE, F3_CAPTURE_LOST_RESPONSE -> PaymentState.CAPTURED;
            default -> throw new PaymentException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Fault not applicable to charge: " + fault,
                    idempotencyKey);
        };
        PaymentState observed = (fault == ThesisFault.F3_CAPTURE_LOST_RESPONSE)
                ? PaymentState.UNKNOWN
                : persisted;

        UUID paymentId = UUID.randomUUID();
        PaymentRow row = new PaymentRow(
                paymentId,
                request.orderId(),
                request.amount().amountMinor(),
                request.amount().currency(),
                persisted);
        try {
            repo.insert(row);
        } catch (DuplicateKeyException e) {
            throw new PaymentException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    HttpStatus.CONFLICT,
                    "Payment already exists for order",
                    idempotencyKey);
        }

        ChargeResponse response = new ChargeResponse(
                paymentId, request.orderId(), request.amount(), observed);
        persistIdempotency(idempotencyKey, "charge", fingerprint, response);
        repo.appendOperationLog(
                "charge",
                paymentId,
                request.orderId(),
                idempotencyKey,
                """
                {"persistedState":"%s","observedResponse":"%s","fault":"%s"}
                """.formatted(persisted, observed, fault).trim());
        return response;
    }

    @Transactional
    public RefundResponse refund(String idempotencyKey, UUID paymentId) {
        IdempotencyRules.requireKey(idempotencyKey);
        String fingerprint = paymentId.toString();
        repo.advisoryLockKey(idempotencyKey);

        var existing = repo.findIdempotencyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint, RefundResponse.class);
        }

        PaymentRow payment = repo.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found", idempotencyKey));

        if (payment.state() == PaymentState.REFUNDED) {
            RefundResponse response = new RefundResponse(paymentId, PaymentState.REFUNDED);
            persistIdempotency(idempotencyKey, "refund", fingerprint, response);
            return response;
        }

        try {
            PaymentTransitions.assertRefundAllowed(payment.state());
        } catch (RuntimeException e) {
            throw new PaymentException(
                    ErrorCode.REFUND_NOT_ALLOWED,
                    HttpStatus.CONFLICT,
                    e.getMessage(),
                    idempotencyKey);
        }

        PaymentState next = PaymentTransitions.refund(payment.state());
        repo.updateState(paymentId, next);
        RefundResponse response = new RefundResponse(paymentId, next);
        persistIdempotency(idempotencyKey, "refund", fingerprint, response);
        repo.appendOperationLog(
                "refund", paymentId, payment.orderId(), idempotencyKey,
                "{\"state\":\"" + next + "\"}");
        return response;
    }

    @Transactional
    public PaymentSnapshot resolve(UUID paymentId, String faultHeader) {
        // Opacity must short-circuit before any lock / FOR UPDATE / operation_log write.
        assertNoTransportOpacity(faultHeader, "resolve");

        repo.advisoryLockPayment(paymentId);
        PaymentRow payment = repo.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found"));

        // Already terminal for resolve path (incl. F3 where DB is already CAPTURED)
        if (payment.state() == PaymentState.CAPTURED
                || payment.state() == PaymentState.FAILED
                || payment.state() == PaymentState.REFUNDED) {
            repo.appendOperationLog(
                    "resolve", paymentId, payment.orderId(), null,
                    "{\"result\":\"already-" + payment.state() + "\"}");
            return toSnapshot(payment);
        }

        ResolveProbeOutcome probe = mapResolveFault(faultHeader);
        PaymentResolveRules.assertNoContradiction(payment.state(), probe);
        PaymentState next = PaymentResolveRules.apply(payment.state(), probe);
        if (next != payment.state()) {
            repo.updateState(paymentId, next);
        }
        repo.appendOperationLog(
                "resolve",
                paymentId,
                payment.orderId(),
                null,
                """
                {"probe":"%s","from":"%s","to":"%s"}
                """.formatted(probe, payment.state(), next).trim());
        return toSnapshot(new PaymentRow(
                payment.paymentId(), payment.orderId(), payment.amountMinor(), payment.currency(), next));
    }

    /**
     * F3-Unresolved: while {@link ThesisFault#RESOLVE_TRANSPORT_OPAQUE} is present and fault
     * injection is enabled, refuse resolve/GET with HTTP 503 and <strong>no</strong> DB mutation.
     */
    private void assertNoTransportOpacity(String faultHeader, String operation) {
        if (!properties.isFaultInjectionEnabled()) {
            return;
        }
        if (faultHeader == null || faultHeader.isBlank()) {
            return;
        }
        final ThesisFault fault;
        try {
            fault = ThesisFault.valueOf(faultHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new PaymentException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Unknown fault header: " + faultHeader);
        }
        if (fault == ThesisFault.RESOLVE_TRANSPORT_OPAQUE) {
            throw new PaymentException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Transport opacity on " + operation + " (RESOLVE_TRANSPORT_OPAQUE); no DB mutation");
        }
    }

    private ThesisFault resolveChargeFault(String faultHeader) {
        if (!properties.isFaultInjectionEnabled()) {
            return ThesisFault.CHARGE_CAPTURE;
        }
        return ThesisFault.parseOrDefault(faultHeader, ThesisFault.CHARGE_CAPTURE);
    }

    private ResolveProbeOutcome mapResolveFault(String faultHeader) {
        if (!properties.isFaultInjectionEnabled()) {
            return ResolveProbeOutcome.INCONCLUSIVE;
        }
        ThesisFault fault = ThesisFault.parseOrDefault(faultHeader, ThesisFault.RESOLVE_INCONCLUSIVE);
        return switch (fault) {
            case RESOLVE_CAPTURE -> ResolveProbeOutcome.CAPTURE_CONFIRMED;
            case RESOLVE_FAIL -> ResolveProbeOutcome.CAPTURE_DID_NOT_OCCUR;
            case RESOLVE_INCONCLUSIVE -> ResolveProbeOutcome.INCONCLUSIVE;
            case RESOLVE_TRANSPORT_OPAQUE -> throw new PaymentException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Transport opacity on resolve (RESOLVE_TRANSPORT_OPAQUE); no DB mutation");
            default -> throw new PaymentException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Fault not applicable to resolve: " + fault);
        };
    }

    private PaymentRow requirePayment(UUID paymentId) {
        return repo.findById(paymentId)
                .orElseThrow(() -> new PaymentException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private PaymentSnapshot toSnapshot(PaymentRow row) {
        return new PaymentSnapshot(row.paymentId(), row.orderId(), row.money(), row.state());
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
