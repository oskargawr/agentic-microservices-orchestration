package pl.edu.pw.thesis.shipping.api;

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
import pl.edu.pw.thesis.contracts.shipping.ArrangeRequest;
import pl.edu.pw.thesis.contracts.shipping.ArrangeResponse;
import pl.edu.pw.thesis.contracts.shipping.CancelShipmentResponse;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;
import pl.edu.pw.thesis.shipping.service.ShippingService;

@RestController
@RequestMapping("/api/v1")
public class ShippingController {

    private final ShippingService service;

    public ShippingController(ShippingService service) {
        this.service = service;
    }

    @PostMapping("/shipments/arrange")
    public ArrangeResponse arrange(
            @RequestHeader(IdempotencyRules.HEADER) String idempotencyKey,
            @Valid @RequestBody ArrangeRequest request) {
        return service.arrange(idempotencyKey, request);
    }

    @PostMapping("/shipments/{shipmentId}/cancel")
    public CancelShipmentResponse cancel(
            @RequestHeader(IdempotencyRules.HEADER) String idempotencyKey,
            @PathVariable UUID shipmentId) {
        return service.cancel(idempotencyKey, shipmentId);
    }

    @GetMapping("/shipments/{shipmentId}")
    public ShipmentSnapshot getShipment(@PathVariable UUID shipmentId) {
        return service.getShipment(shipmentId);
    }
}
