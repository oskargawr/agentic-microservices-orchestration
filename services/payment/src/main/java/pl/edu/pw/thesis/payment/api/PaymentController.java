package pl.edu.pw.thesis.payment.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;
import pl.edu.pw.thesis.contracts.payment.ChargeRequest;
import pl.edu.pw.thesis.contracts.payment.ChargeResponse;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.payment.RefundResponse;
import pl.edu.pw.thesis.payment.fault.ThesisFault;
import pl.edu.pw.thesis.payment.service.PaymentService;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/payments/charge")
    public ChargeResponse charge(
            @RequestHeader(IdempotencyRules.HEADER) String idempotencyKey,
            @RequestHeader(value = ThesisFault.HEADER, required = false) String fault,
            @Valid @RequestBody ChargeRequest request) {
        return service.charge(idempotencyKey, request, fault);
    }

    @PostMapping("/payments/{paymentId}/refund")
    public RefundResponse refund(
            @RequestHeader(IdempotencyRules.HEADER) String idempotencyKey,
            @PathVariable UUID paymentId) {
        return service.refund(idempotencyKey, paymentId);
    }

    @PostMapping("/payments/{paymentId}/resolve")
    public PaymentSnapshot resolve(
            @PathVariable UUID paymentId,
            @RequestHeader(value = ThesisFault.HEADER, required = false) String fault) {
        return service.resolve(paymentId, fault);
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentSnapshot getPayment(
            @PathVariable UUID paymentId,
            @RequestHeader(value = ThesisFault.HEADER, required = false) String fault) {
        return service.getPayment(paymentId, fault);
    }
}
