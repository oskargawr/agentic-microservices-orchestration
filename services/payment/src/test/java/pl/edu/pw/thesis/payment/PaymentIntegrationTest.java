package pl.edu.pw.thesis.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;
import pl.edu.pw.thesis.payment.fault.ThesisFault;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("postgresAvailable")
class PaymentIntegrationTest {

    static final String JDBC_URL = System.getenv().getOrDefault(
            "PAYMENT_TEST_JDBC_URL",
            "jdbc:postgresql://localhost:5433/business?currentSchema=payment");
    static final String JDBC_USER = System.getenv().getOrDefault("PAYMENT_TEST_USER", "thesis");
    static final String JDBC_PASSWORD = System.getenv().getOrDefault("PAYMENT_TEST_PASSWORD", "thesis");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> JDBC_USER);
        registry.add("spring.datasource.password", () -> JDBC_PASSWORD);
    }

    static boolean postgresAvailable() {
        try (var c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("DELETE FROM idempotency_records");
        jdbc.update("DELETE FROM payments");
    }

    @Test
    void successfulCapture() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(charge(orderId, orderId + ":charge", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CAPTURED"));
    }

    @Test
    void confirmedFailure() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(charge(orderId, orderId + ":charge", ThesisFault.CHARGE_FAIL.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
    }

    @Test
    void unknownOutcome() throws Exception {
        UUID orderId = UUID.randomUUID();
        String paymentId = chargeReturningId(orderId, orderId + ":charge", ThesisFault.CHARGE_UNKNOWN.name());
        mockMvc.perform(get("/api/v1/payments/" + paymentId))
                .andExpect(jsonPath("$.state").value("UNKNOWN"));
    }

    @Test
    void resolveUnknownToCaptured() throws Exception {
        UUID orderId = UUID.randomUUID();
        String paymentId = chargeReturningId(orderId, orderId + ":charge", ThesisFault.CHARGE_UNKNOWN.name());
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/resolve")
                        .header(ThesisFault.HEADER, ThesisFault.RESOLVE_CAPTURE.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CAPTURED"));
    }

    @Test
    void resolveUnknownToFailedWithAuthoritativeEvidence() throws Exception {
        UUID orderId = UUID.randomUUID();
        String paymentId = chargeReturningId(orderId, orderId + ":charge", ThesisFault.CHARGE_UNKNOWN.name());
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/resolve")
                        .header(ThesisFault.HEADER, ThesisFault.RESOLVE_FAIL.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
    }

    @Test
    void inconclusiveResolvePreservesUnknown() throws Exception {
        UUID orderId = UUID.randomUUID();
        String paymentId = chargeReturningId(orderId, orderId + ":charge", ThesisFault.CHARGE_UNKNOWN.name());
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/resolve")
                        .header(ThesisFault.HEADER, ThesisFault.RESOLVE_INCONCLUSIVE.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNKNOWN"));
    }

    @Test
    void refundCapturedAndRefuseOtherwise() throws Exception {
        UUID orderId = UUID.randomUUID();
        String capturedId = chargeReturningId(orderId, orderId + ":charge", null);
        mockMvc.perform(post("/api/v1/payments/" + capturedId + "/refund")
                        .header(IdempotencyRules.HEADER, capturedId + ":refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REFUNDED"));

        UUID o2 = UUID.randomUUID();
        String unknownId = chargeReturningId(o2, o2 + ":charge", ThesisFault.CHARGE_UNKNOWN.name());
        mockMvc.perform(post("/api/v1/payments/" + unknownId + "/refund")
                        .header(IdempotencyRules.HEADER, unknownId + ":refund"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_ALLOWED"));

        UUID o3 = UUID.randomUUID();
        String failedId = chargeReturningId(o3, o3 + ":charge", ThesisFault.CHARGE_FAIL.name());
        mockMvc.perform(post("/api/v1/payments/" + failedId + "/refund")
                        .header(IdempotencyRules.HEADER, failedId + ":refund"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_ALLOWED"));
    }

    @Test
    void f3CapturePersistedDespiteLostResponseThenResolveDiscovers() throws Exception {
        UUID orderId = UUID.randomUUID();
        MvcResult chargeResult = mockMvc.perform(charge(
                        orderId, orderId + ":charge", ThesisFault.F3_CAPTURE_LOST_RESPONSE.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNKNOWN"))
                .andReturn();
        String paymentId = objectMapper.readTree(chargeResult.getResponse().getContentAsString())
                .get("paymentId").asText();

        // Oracle / DB truth
        String dbState = jdbc.queryForObject(
                "SELECT state FROM payments WHERE payment_id = ?::uuid", String.class, paymentId);
        assertThat(dbState).isEqualTo("CAPTURED");

        mockMvc.perform(get("/api/v1/payments/" + paymentId))
                .andExpect(jsonPath("$.state").value("CAPTURED"));

        String detail = jdbc.queryForObject(
                """
                SELECT detail FROM operation_log
                WHERE payment_id = ?::uuid AND operation = 'charge'
                ORDER BY id DESC LIMIT 1
                """,
                String.class, paymentId);
        assertThat(detail).contains("\"persistedState\":\"CAPTURED\"");
        assertThat(detail).contains("\"observedResponse\":\"UNKNOWN\"");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CAPTURED"));

        // Timeout observation alone must not imply failure — DB never FAILED
        assertThat(jdbc.queryForObject(
                "SELECT state FROM payments WHERE payment_id = ?::uuid", String.class, paymentId))
                .isNotEqualTo("FAILED");
    }

    @Test
    void noRefundBasedOnTimeoutObservationAlone() throws Exception {
        // Caller saw UNKNOWN from F3 but must use CAPTURED business state for refund.
        // Refunding UNKNOWN-without-capture is refused (CHARGE_UNKNOWN case already covered).
        // Here: ensure FAILED is never inferred from F3 timeout path.
        UUID orderId = UUID.randomUUID();
        String paymentId = chargeReturningId(
                orderId, orderId + ":charge", ThesisFault.F3_CAPTURE_LOST_RESPONSE.name());
        Long failedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE payment_id = ?::uuid AND state = 'FAILED'",
                Long.class, paymentId);
        assertThat(failedCount).isZero();
        // Refund is allowed only because capture IS confirmed in DB (not because of timeout)
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refund")
                        .header(IdempotencyRules.HEADER, paymentId + ":refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REFUNDED"));
    }

    @Test
    void idempotentChargeSameKeyAndConflict() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":charge";
        String first = chargeReturningId(orderId, key, null);
        String second = chargeReturningId(orderId, key, null);
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/payments/charge")
                        .header(IdempotencyRules.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeJson(orderId, 200)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void concurrentDuplicateCharge() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":charge";
        String body = chargeJson(orderId, 100);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                MvcResult r = mockMvc.perform(post("/api/v1/payments/charge")
                                .header(IdempotencyRules.HEADER, key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                return objectMapper.readTree(r.getResponse().getContentAsString())
                        .get("paymentId").asText();
            });
        }
        List<Future<String>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        String first = futures.getFirst().get();
        for (Future<String> f : futures) {
            assertThat(f.get()).isEqualTo(first);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateRefund() throws Exception {
        UUID orderId = UUID.randomUUID();
        String paymentId = chargeReturningId(orderId, orderId + ":charge", null);
        String key = paymentId + ":refund";
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger ok = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                MvcResult r = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refund")
                                .header(IdempotencyRules.HEADER, key))
                        .andReturn();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                        .get("state").asText()).isEqualTo("REFUNDED");
                ok.incrementAndGet();
                return null;
            });
        }
        pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        assertThat(ok.get()).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM payments WHERE payment_id = ?::uuid", String.class, paymentId))
                .isEqualTo("REFUNDED");
        Long refundOps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE payment_id = ?::uuid AND operation = 'refund'",
                Long.class, paymentId);
        assertThat(refundOps).isEqualTo(1);
    }

    @Test
    void concurrentResolveDoesNotContradict() throws Exception {
        UUID orderId = UUID.randomUUID();
        String paymentId = chargeReturningId(orderId, orderId + ":charge", ThesisFault.CHARGE_UNKNOWN.name());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> a = pool.submit(() -> mockMvc.perform(post("/api/v1/payments/" + paymentId + "/resolve")
                        .header(ThesisFault.HEADER, ThesisFault.RESOLVE_CAPTURE.name()))
                .andReturn().getResponse().getContentAsString());
        Future<String> b = pool.submit(() -> mockMvc.perform(post("/api/v1/payments/" + paymentId + "/resolve")
                        .header(ThesisFault.HEADER, ThesisFault.RESOLVE_CAPTURE.name()))
                .andReturn().getResponse().getContentAsString());
        JsonNode na = objectMapper.readTree(a.get(30, TimeUnit.SECONDS));
        JsonNode nb = objectMapper.readTree(b.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(na.get("state").asText()).isEqualTo("CAPTURED");
        assertThat(nb.get("state").asText()).isEqualTo("CAPTURED");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM payments WHERE payment_id = ?::uuid", String.class, paymentId))
                .isEqualTo("CAPTURED");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder charge(
            UUID orderId, String key, String fault) {
        var req = post("/api/v1/payments/charge")
                .header(IdempotencyRules.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeJson(orderId, 100));
        if (fault != null) {
            req.header(ThesisFault.HEADER, fault);
        }
        return req;
    }

    private String chargeReturningId(UUID orderId, String key, String fault) throws Exception {
        MvcResult r = mockMvc.perform(charge(orderId, key, fault)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("paymentId").asText();
    }

    private static String chargeJson(UUID orderId, long amountMinor) {
        return """
                {"orderId":"%s","amount":{"amountMinor":%d,"currency":"PLN"}}
                """.formatted(orderId, amountMinor);
    }
}
