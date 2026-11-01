package pl.edu.pw.thesis.orchestration.console.dto;

import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.StockSnapshot;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;

public record BusinessStateResponse(
        boolean available,
        String reason,
        ReservationSnapshot reservation,
        PaymentSnapshot payment,
        ShipmentSnapshot shipment,
        StockSnapshot stock,
        BusinessConsistencyCheck consistencyCheck
) {
}
