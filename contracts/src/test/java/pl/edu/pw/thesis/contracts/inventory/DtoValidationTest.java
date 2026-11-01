package pl.edu.pw.thesis.contracts.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DtoValidationTest {

    @Test
    void reserveRejectsNonPositiveQty() {
        assertThrows(IllegalArgumentException.class, () ->
                new ReserveRequest(UUID.randomUUID(), "SKU-MVP-1", 0, "key"));
    }

    @Test
    void reserveRejectsBlankIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new ReserveRequest(UUID.randomUUID(), "SKU-MVP-1", 1, " "));
    }
}
