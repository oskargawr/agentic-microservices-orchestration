package pl.edu.pw.thesis.inventory.api;

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
import pl.edu.pw.thesis.contracts.inventory.ConsumeResponse;
import pl.edu.pw.thesis.contracts.inventory.ReleaseResponse;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReserveRequest;
import pl.edu.pw.thesis.contracts.inventory.ReserveResponse;
import pl.edu.pw.thesis.contracts.inventory.StockSnapshot;
import pl.edu.pw.thesis.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping("/reservations")
    public ReserveResponse reserve(
            @RequestHeader(IdempotencyRules.HEADER) String idempotencyKey,
            @Valid @RequestBody ReserveRequest request) {
        return service.reserve(idempotencyKey, request);
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ReleaseResponse release(
            @RequestHeader(IdempotencyRules.HEADER) String idempotencyKey,
            @PathVariable UUID reservationId) {
        return service.release(idempotencyKey, reservationId);
    }

    @PostMapping("/reservations/{reservationId}/consume")
    public ConsumeResponse consume(
            @RequestHeader(IdempotencyRules.HEADER) String idempotencyKey,
            @PathVariable UUID reservationId) {
        return service.consume(idempotencyKey, reservationId);
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationSnapshot getReservation(@PathVariable UUID reservationId) {
        return service.getReservation(reservationId);
    }

    @GetMapping("/stock/{sku}")
    public StockSnapshot getStock(@PathVariable String sku) {
        return service.getStock(sku);
    }
}
