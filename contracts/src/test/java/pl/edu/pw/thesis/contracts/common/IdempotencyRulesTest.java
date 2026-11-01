package pl.edu.pw.thesis.contracts.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IdempotencyRulesTest {

    @Test
    void sameKeySamePayloadIsCompatible() {
        assertDoesNotThrow(() ->
                IdempotencyRules.assertPayloadCompatible("k1", "fp-a", "fp-a"));
    }

    @Test
    void sameKeyDifferentPayloadConflicts() {
        IdempotencyConflictException ex = assertThrows(
                IdempotencyConflictException.class,
                () -> IdempotencyRules.assertPayloadCompatible("k1", "fp-a", "fp-b"));
        assertEquals("k1", ex.idempotencyKey());
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, ex.toApiError().code());
    }
}
