package pl.edu.pw.thesis.experiment.oracle.snapshot;

import java.util.UUID;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;

public record ShipmentView(UUID shipmentId, UUID orderId, ShipmentState state) {}
