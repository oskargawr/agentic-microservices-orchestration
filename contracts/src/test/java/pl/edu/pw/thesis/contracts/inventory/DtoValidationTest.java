package pl.edu.pw.thesis.contracts.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DtoValidationTest {

    @Test
    void reserveRejectsNonPositiveQty() {
        assertThrows(IllegalArgumentException.class, () ->
                new ReserveRequest(UUID.randomUUID(), "SKU-MVP-1", 0));
    }

    @Test
    void reserveRejectsBlankSku() {
        assertThrows(IllegalArgumentException.class, () ->
                new ReserveRequest(UUID.randomUUID(), " ", 1));
    }
}
