package pl.edu.pw.thesis.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import pl.edu.pw.thesis.shipping.fault.ShippingFault;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("postgresAvailable")
class ShippingIntegrationTest {

    static final String JDBC_URL = System.getenv().getOrDefault(
            "SHIPPING_TEST_JDBC_URL",
            "jdbc:postgresql://localhost:5433/business?currentSchema=shipping");
    static final String JDBC_USER = System.getenv().getOrDefault("SHIPPING_TEST_USER", "thesis");
    static final String JDBC_PASSWORD = System.getenv().getOrDefault("SHIPPING_TEST_PASSWORD", "thesis");

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
        jdbc.update("DELETE FROM shipments");
    }

    @Test
    void arrangeSuccessAndSnapshot() throws Exception {
        UUID orderId = UUID.randomUUID();
        String shipmentId = arrange(orderId, orderId + ":arrange");
        mockMvc.perform(get("/api/v1/shipments/" + shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ARRANGED"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void cancelCompensation() throws Exception {
        UUID orderId = UUID.randomUUID();
        String shipmentId = arrange(orderId, orderId + ":arrange");
        mockMvc.perform(post("/api/v1/shipments/" + shipmentId + "/cancel")
                        .header(IdempotencyRules.HEADER, shipmentId + ":cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/shipments/" + shipmentId))
                .andExpect(jsonPath("$.state").value("CANCELLED"));
    }

    @Test
    void repeatedCancelIsIdempotent() throws Exception {
        UUID orderId = UUID.randomUUID();
        String shipmentId = arrange(orderId, orderId + ":arrange");
        String key = shipmentId + ":cancel";
        mockMvc.perform(post("/api/v1/shipments/" + shipmentId + "/cancel")
                        .header(IdempotencyRules.HEADER, key))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/shipments/" + shipmentId + "/cancel")
                        .header(IdempotencyRules.HEADER, key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"));

        Long cancelOps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE shipment_id = ?::uuid AND operation = 'cancel'",
                Long.class, shipmentId);
        assertThat(cancelOps).isEqualTo(1);
    }

    @Test
    void unknownShipment() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void idempotentArrangeSameKeyAndConflict() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":arrange";
        String first = arrange(orderId, key);
        String second = arrange(orderId, key);
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipments", Long.class)).isEqualTo(1);

        UUID other = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/shipments/arrange")
                        .header(IdempotencyRules.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + other + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void concurrentDuplicateArrange() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":arrange";
        String body = "{\"orderId\":\"" + orderId + "\"}";
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                MvcResult r = mockMvc.perform(post("/api/v1/shipments/arrange")
                                .header(IdempotencyRules.HEADER, key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                return objectMapper.readTree(r.getResponse().getContentAsString())
                        .get("shipmentId").asText();
            });
        }
        List<Future<String>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        String first = futures.getFirst().get();
        for (Future<String> f : futures) {
            assertThat(f.get()).isEqualTo(first);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipments", Long.class)).isEqualTo(1);
    }

    @Test
    void arrangeRejected_noShipmentRow() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":arrange";
        mockMvc.perform(post("/api/v1/shipments/arrange")
                        .header(IdempotencyRules.HEADER, key)
                        .header(ShippingFault.HEADER, ShippingFault.ARRANGE_REJECTED.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARRANGE_REJECTED"));

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM shipments WHERE order_id = ?::uuid", Long.class, orderId))
                .isZero();
        Integer httpStatus = jdbc.queryForObject(
                "SELECT http_status FROM idempotency_records WHERE idempotency_key = ?",
                Integer.class,
                key);
        assertThat(httpStatus).isEqualTo(409);
    }

    @Test
    void arrangeRejected_idempotentReplayWithoutHeader() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":arrange";
        mockMvc.perform(post("/api/v1/shipments/arrange")
                        .header(IdempotencyRules.HEADER, key)
                        .header(ShippingFault.HEADER, ShippingFault.ARRANGE_REJECTED.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARRANGE_REJECTED"));

        // Replay after "disarm": same key, no fault header — still ARRANGE_REJECTED
        mockMvc.perform(post("/api/v1/shipments/arrange")
                        .header(IdempotencyRules.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARRANGE_REJECTED"));

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM shipments WHERE order_id = ?::uuid", Long.class, orderId))
                .isZero();
        Long arrangeOps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE order_id = ?::uuid AND operation = 'arrange'",
                Long.class,
                orderId);
        assertThat(arrangeOps).isEqualTo(1);
    }

    @Test
    void arrangeRejected_fingerprintConflict() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":arrange";
        mockMvc.perform(post("/api/v1/shipments/arrange")
                        .header(IdempotencyRules.HEADER, key)
                        .header(ShippingFault.HEADER, ShippingFault.ARRANGE_REJECTED.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARRANGE_REJECTED"));

        UUID other = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/shipments/arrange")
                        .header(IdempotencyRules.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + other + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void concurrentCancelSameShipment() throws Exception {
        UUID orderId = UUID.randomUUID();
        String shipmentId = arrange(orderId, orderId + ":arrange");
        String key = shipmentId + ":cancel";
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger ok = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                MvcResult r = mockMvc.perform(post("/api/v1/shipments/" + shipmentId + "/cancel")
                                .header(IdempotencyRules.HEADER, key))
                        .andReturn();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                        .get("state").asText()).isEqualTo("CANCELLED");
                ok.incrementAndGet();
                return null;
            });
        }
        pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        assertThat(ok.get()).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM shipments WHERE shipment_id = ?::uuid", String.class, shipmentId))
                .isEqualTo("CANCELLED");
        Long cancelOps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE shipment_id = ?::uuid AND operation = 'cancel'",
                Long.class, shipmentId);
        assertThat(cancelOps).isEqualTo(1);
    }

    private String arrange(UUID orderId, String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/shipments/arrange")
                        .header(IdempotencyRules.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ARRANGED"))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("shipmentId").asText();
    }
}
