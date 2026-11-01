package pl.edu.pw.thesis.orchestration.console.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartRunRequest(
        @NotNull UUID orderId,
        @NotBlank String sku,
        @Min(1) int qty,
        @Min(1) long amountMinor,
        @NotBlank String currency
) {
}
