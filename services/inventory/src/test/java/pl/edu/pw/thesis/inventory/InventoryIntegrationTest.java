package pl.edu.pw.thesis.inventory;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.edu.pw.thesis.contracts.common.IdempotencyRules;

/**
 * Integration tests against real PostgreSQL (not H2).
 *
 * <p>Default: local thesis Compose Postgres on {@code localhost:5433}
 * ({@code cd infra && docker compose up -d}).
 * Override with env {@code INVENTORY_TEST_JDBC_URL} / {@code INVENTORY_TEST_USER} /
 * {@code INVENTORY_TEST_PASSWORD} (used by CI service container).
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("postgresAvailable")
class InventoryIntegrationTest {

    static final String JDBC_URL = System.getenv().getOrDefault(
            "INVENTORY_TEST_JDBC_URL",
            "jdbc:postgresql://localhost:5433/business?currentSchema=inventory");
    static final String JDBC_USER = System.getenv().getOrDefault("INVENTORY_TEST_USER", "thesis");
    static final String JDBC_PASSWORD = System.getenv().getOrDefault("INVENTORY_TEST_PASSWORD", "thesis");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> JDBC_USER);
        registry.add("spring.datasource.password", () -> JDBC_PASSWORD);
        registry.add("spring.flyway.enabled", () -> "true");
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
    void cleanState() {
        resetStock();
    }

    @Test
    void reserveSuccessAndStockSnapshot() throws Exception {
        UUID orderId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/v1/reservations")
                        .header(IdempotencyRules.HEADER, orderId + ":reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","sku":"SKU-MVP-1","qty":2}
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("HELD"))
                .andExpect(jsonPath("$.qty").value(2))
                .andReturn();

        String reservationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reservationId").asText();

        mockMvc.perform(get("/api/v1/stock/SKU-MVP-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(100))
                .andExpect(jsonPath("$.held").value(2));

        mockMvc.perform(get("/api/v1/reservations/" + reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("HELD"));
    }

    @Test
    void insufficientStock() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/reservations")
                        .header(IdempotencyRules.HEADER, orderId + ":reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","sku":"SKU-MVP-1","qty":1000}
                                """.formatted(orderId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void releaseAndConsumeHappyPaths() throws Exception {
        UUID orderId = UUID.randomUUID();
        String reservationId = reserve(orderId, 1, orderId + ":reserve");

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/release")
                        .header(IdempotencyRules.HEADER, reservationId + ":release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RELEASED"));

        mockMvc.perform(get("/api/v1/stock/SKU-MVP-1"))
                .andExpect(jsonPath("$.held").value(0))
                .andExpect(jsonPath("$.onHand").value(100));

        UUID order2 = UUID.randomUUID();
        String res2 = reserve(order2, 3, order2 + ":reserve");
        mockMvc.perform(post("/api/v1/reservations/" + res2 + "/consume")
                        .header(IdempotencyRules.HEADER, res2 + ":consume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONSUMED"));

        mockMvc.perform(get("/api/v1/stock/SKU-MVP-1"))
                .andExpect(jsonPath("$.held").value(0))
                .andExpect(jsonPath("$.onHand").value(97));
    }

    @Test
    void invalidTransitionConsumeAfterRelease() throws Exception {
        UUID orderId = UUID.randomUUID();
        String reservationId = reserve(orderId, 1, orderId + ":reserve");
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/release")
                        .header(IdempotencyRules.HEADER, reservationId + ":release"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/consume")
                        .header(IdempotencyRules.HEADER, reservationId + ":consume"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void unknownReservationAndSku() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/stock/NO-SUCH-SKU"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void idempotentReserveSameKeySamePayload() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":reserve";
        String first = reserve(orderId, 1, key);
        String second = reserve(orderId, 1, key);
        assertThat(second).isEqualTo(first);

        Integer held = jdbc.queryForObject("SELECT held FROM stock WHERE sku = 'SKU-MVP-1'", Integer.class);
        assertThat(held).isEqualTo(1);
    }

    @Test
    void idempotencyConflictDifferentPayload() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = "conflict-key-" + orderId;
        reserve(orderId, 1, key);

        mockMvc.perform(post("/api/v1/reservations")
                        .header(IdempotencyRules.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","sku":"SKU-MVP-1","qty":2}
                                """.formatted(orderId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void repeatedReleaseAndConsumeDoNotDoubleApply() throws Exception {
        UUID orderId = UUID.randomUUID();
        String reservationId = reserve(orderId, 5, orderId + ":reserve");

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/consume")
                        .header(IdempotencyRules.HEADER, reservationId + ":consume"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/consume")
                        .header(IdempotencyRules.HEADER, reservationId + ":consume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONSUMED"));

        Integer onHand = jdbc.queryForObject("SELECT on_hand FROM stock WHERE sku = 'SKU-MVP-1'", Integer.class);
        Integer held = jdbc.queryForObject("SELECT held FROM stock WHERE sku = 'SKU-MVP-1'", Integer.class);
        assertThat(held).isEqualTo(0);
        assertThat(onHand).isEqualTo(95);

        Long consumeOps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE reservation_id = ?::uuid AND operation = 'consume'",
                Long.class, reservationId);
        assertThat(consumeOps).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateReserveSameKey() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":reserve";
        String payload = """
                {"orderId":"%s","sku":"SKU-MVP-1","qty":1}
                """.formatted(orderId);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                MvcResult r = mockMvc.perform(post("/api/v1/reservations")
                                .header(IdempotencyRules.HEADER, key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andReturn();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                return objectMapper.readTree(r.getResponse().getContentAsString())
                        .get("reservationId").asText();
            });
        }
        List<Future<String>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        String first = futures.getFirst().get();
        for (Future<String> f : futures) {
            assertThat(f.get()).isEqualTo(first);
        }
        Integer held = jdbc.queryForObject("SELECT held FROM stock WHERE sku = 'SKU-MVP-1'", Integer.class);
        assertThat(held).isEqualTo(1);
        Long reservations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE order_id = ?::uuid",
                Long.class, orderId.toString());
        assertThat(reservations).isEqualTo(1);
    }

    @Test
    void concurrentReservationsDoNotOversell() throws Exception {
        resetStockTo(5);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID orderId = UUID.randomUUID();
            tasks.add(() -> {
                MvcResult r = mockMvc.perform(post("/api/v1/reservations")
                                .header(IdempotencyRules.HEADER, orderId + ":reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"orderId":"%s","sku":"SKU-MVP-1","qty":1}
                                        """.formatted(orderId)))
                        .andReturn();
                if (r.getResponse().getStatus() == 200) {
                    success.incrementAndGet();
                } else {
                    assertThat(r.getResponse().getStatus()).isEqualTo(409);
                    insufficient.incrementAndGet();
                }
                return null;
            });
        }
        pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(5);
        assertThat(insufficient.get()).isEqualTo(5);
        Integer held = jdbc.queryForObject("SELECT held FROM stock WHERE sku = 'SKU-MVP-1'", Integer.class);
        Integer onHand = jdbc.queryForObject("SELECT on_hand FROM stock WHERE sku = 'SKU-MVP-1'", Integer.class);
        assertThat(held).isEqualTo(5);
        assertThat(onHand).isEqualTo(5);
    }

    @Test
    void concurrentConsumeAndReleaseSameReservation() throws Exception {
        UUID orderId = UUID.randomUUID();
        String reservationId = reserve(orderId, 1, orderId + ":reserve");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> consumeFuture = pool.submit(() -> mockMvc.perform(
                        post("/api/v1/reservations/" + reservationId + "/consume")
                                .header(IdempotencyRules.HEADER, reservationId + ":consume"))
                .andReturn().getResponse().getStatus());
        Future<Integer> releaseFuture = pool.submit(() -> mockMvc.perform(
                        post("/api/v1/reservations/" + reservationId + "/release")
                                .header(IdempotencyRules.HEADER, reservationId + ":release"))
                .andReturn().getResponse().getStatus());

        int consumeStatus = consumeFuture.get(30, TimeUnit.SECONDS);
        int releaseStatus = releaseFuture.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(List.of(consumeStatus, releaseStatus)).contains(200);
        assertThat(List.of(consumeStatus, releaseStatus)).contains(409);

        String state = jdbc.queryForObject(
                "SELECT state FROM reservations WHERE reservation_id = ?::uuid",
                String.class, reservationId);
        assertThat(state).isIn("CONSUMED", "RELEASED");

        Integer held = jdbc.queryForObject("SELECT held FROM stock WHERE sku = 'SKU-MVP-1'", Integer.class);
        assertThat(held).isEqualTo(0);
        Integer onHand = jdbc.queryForObject("SELECT on_hand FROM stock WHERE sku = 'SKU-MVP-1'", Integer.class);
        if ("CONSUMED".equals(state)) {
            assertThat(onHand).isEqualTo(99);
        } else {
            assertThat(onHand).isEqualTo(100);
        }
    }

    private String reserve(UUID orderId, int qty, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reservations")
                        .header(IdempotencyRules.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","sku":"SKU-MVP-1","qty":%d}
                                """.formatted(orderId, qty)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reservationId").asText();
    }

    private void resetStock() {
        resetStockTo(100);
    }

    private void resetStockTo(int onHand) {
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("DELETE FROM idempotency_records");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("UPDATE stock SET on_hand = ?, held = 0 WHERE sku = 'SKU-MVP-1'", onHand);
    }
}
