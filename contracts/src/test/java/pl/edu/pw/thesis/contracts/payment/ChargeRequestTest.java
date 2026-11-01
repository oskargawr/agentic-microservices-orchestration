package pl.edu.pw.thesis.contracts.payment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.edu.pw.thesis.contracts.common.Money;

class ChargeRequestTest {

    @Test
    void amountMustBeStrictlyPositive() {
        assertThrows(IllegalArgumentException.class, () ->
                new ChargeRequest(UUID.randomUUID(), Money.plnMinor(0)));
        assertThrows(IllegalArgumentException.class, () ->
                new ChargeRequest(UUID.randomUUID(), Money.plnMinor(-1)));
    }
}
