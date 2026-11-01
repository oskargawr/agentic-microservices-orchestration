package pl.edu.pw.thesis.orchestration.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.edu.pw.thesis.orchestration.varianta.ServiceEndpoints;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalClients;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;
import pl.edu.pw.thesis.orchestration.varianta.runtime.VariantAWorkers;

/**
 * TH-205 acceptance: real Temporal server + real HTTP services + console BFF.
 * Skips only when Temporal or Postgres is unreachable (local/CI without full stack).
 */
@SpringBootTest(properties = {
        "server.address=127.0.0.1",
        "thesis.temporal.target=${TEMPORAL_TARGET:localhost:7233}",
        "thesis.temporal.namespace=default",
        "thesis.services.inventory-base-url=http://127.0.0.1:18081",
        "thesis.services.payment-base-url=http://127.0.0.1:18082",
        "thesis.services.shipping-base-url=http://127.0.0.1:18083"
})
@AutoConfigureMockMvc
@EnabledIf("stackAvailable")
class ConsoleRealTemporalE2EIT {

    static final String JDBC = System.getenv().getOrDefault(
            "VARIANT_A_TEST_JDBC_URL", "jdbc:postgresql://localhost:5433/business");
    static final String USER = System.getenv().getOrDefault("VARIANT_A_TEST_USER", "thesis");
    static final String PASS = System.getenv().getOrDefault("VARIANT_A_TEST_PASSWORD", "thesis");
    static final String TEMPORAL_TARGET =
            System.getenv().getOrDefault("TEMPORAL_TARGET", "localhost:7233");

    static final int INV_PORT = 18081;
    static final int PAY_PORT = 18082;
    static final int SHIP_PORT = 18083;

    static final List<Process> PROCESSES = new ArrayList<>();
    static WorkerFactory workerFactory;
    static WorkflowServiceStubs workerStubs;

    static boolean stackAvailable() {
        return postgresAvailable() && temporalAvailable();
    }

    static boolean postgresAvailable() {
        try (var c = DriverManager.getConnection(JDBC, USER, PASS)) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean temporalAvailable() {
        WorkflowServiceStubs stubs = null;
        try {
            TemporalSettings settings = new TemporalSettings(TEMPORAL_TARGET, "default");
            stubs = TemporalClients.newServiceStubs(settings);
            stubs.blockingStub().getSystemInfo(
                    io.temporal.api.workflowservice.v1.GetSystemInfoRequest.newBuilder().build());
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (stubs != null) {
                stubs.shutdown();
            }
        }
    }

    @BeforeAll
    static void startStack() throws Exception {
        PROCESSES.add(startService("inventory", INV_PORT, "inventory"));
        PROCESSES.add(startService("payment", PAY_PORT, "payment"));
        PROCESSES.add(startService("shipping", SHIP_PORT, "shipping"));
        awaitHealthy(INV_PORT);
        awaitHealthy(PAY_PORT);
        awaitHealthy(SHIP_PORT);

        TemporalSettings settings = new TemporalSettings(TEMPORAL_TARGET, "default");
        workerStubs = TemporalClients.newServiceStubs(settings);
        WorkflowClient client = TemporalClients.newWorkflowClient(workerStubs, settings);
        workerFactory = VariantAWorkers.startWorkerFactory(
                client,
                new ServiceEndpoints(
                        "http://127.0.0.1:" + INV_PORT,
                        "http://127.0.0.1:" + PAY_PORT,
                        "http://127.0.0.1:" + SHIP_PORT));
    }

    @AfterAll
    static void stopStack() {
        if (workerFactory != null) {
            workerFactory.shutdown();
        }
        if (workerStubs != null) {
            workerStubs.shutdown();
        }
        for (Process p : PROCESSES) {
            p.destroy();
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
        PROCESSES.clear();
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetData() throws Exception {
        exec("inventory",
                "DELETE FROM operation_log",
                "DELETE FROM idempotency_records",
                "DELETE FROM reservations",
                "UPDATE stock SET on_hand = 100, held = 0 WHERE sku = 'SKU-MVP-1'");
        exec("payment",
                "DELETE FROM operation_log",
                "DELETE FROM idempotency_records",
                "DELETE FROM payments");
        exec("shipping",
                "DELETE FROM operation_log",
                "DELETE FROM idempotency_records",
                "DELETE FROM shipments");
    }

    @Test
    void happyPathThroughRealTemporalAndDuplicateStartRejected() throws Exception {
        UUID orderId = UUID.randomUUID();
        String body = """
                {
                  "orderId": "%s",
                  "sku": "SKU-MVP-1",
                  "qty": 1,
                  "amountMinor": 2599,
                  "currency": "PLN"
                }
                """.formatted(orderId);

        MvcResult start = mockMvc.perform(post("/api/console/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("order-" + orderId))
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andReturn();

        String workflowId = objectMapper.readTree(start.getResponse().getContentAsString())
                .get("workflowId").asText();

        mockMvc.perform(post("/api/console/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        JsonNode status = awaitCompleted(workflowId);
        assertThat(status.get("temporalStatus").asText()).isEqualTo("COMPLETED");
        assertThat(status.get("steps")).hasSize(4);
        for (JsonNode step : status.get("steps")) {
            assertThat(step.get("status").asText()).isEqualTo("COMPLETED");
        }
        assertThat(status.get("workflowResult").get("reservationState").asText()).isEqualTo("CONSUMED");
        assertThat(status.get("workflowResult").get("paymentState").asText()).isEqualTo("CAPTURED");
        assertThat(status.get("workflowResult").get("shipmentState").asText()).isEqualTo("ARRANGED");

        MvcResult business = mockMvc.perform(get("/api/console/runs/" + workflowId + "/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reservation.state").value("CONSUMED"))
                .andExpect(jsonPath("$.payment.state").value("CAPTURED"))
                .andExpect(jsonPath("$.shipment.state").value("ARRANGED"))
                .andExpect(jsonPath("$.consistencyCheck.looksConsistentWithConfirmedSuccess").value(true))
                .andReturn();

        JsonNode biz = objectMapper.readTree(business.getResponse().getContentAsString());
        assertThat(biz.get("stock").get("onHand").asInt()).isEqualTo(99);
        assertThat(biz.get("stock").get("held").asInt()).isEqualTo(0);

        System.out.println("TH-205 E2E workflowId=" + workflowId
                + " reservation=" + biz.get("reservation").get("state").asText()
                + " payment=" + biz.get("payment").get("state").asText()
                + " shipment=" + biz.get("shipment").get("state").asText());
    }

    private JsonNode awaitCompleted(String workflowId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/console/runs/" + workflowId))
                    .andExpect(status().isOk())
                    .andReturn();
            last = objectMapper.readTree(result.getResponse().getContentAsString());
            String temporalStatus = last.get("temporalStatus").asText();
            if ("COMPLETED".equals(temporalStatus)
                    || "FAILED".equals(temporalStatus)
                    || "TERMINATED".equals(temporalStatus)
                    || "TIMED_OUT".equals(temporalStatus)) {
                return last;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Workflow did not finish in time: " + last);
    }

    private static Process startService(String name, int port, String schema) throws Exception {
        Path jar = bootJar(name);
        String url = schemaUrl(schema);
        String java = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(
                java, "-jar", jar.toAbsolutePath().toString(),
                "--server.port=" + port,
                "--server.address=127.0.0.1",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + USER,
                "--spring.datasource.password=" + PASS,
                "--spring.flyway.schemas=" + schema,
                "--spring.flyway.default-schema=" + schema,
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN");
        pb.redirectErrorStream(true);
        Path log = Files.createTempFile("th205-" + name + "-", ".log");
        pb.redirectOutput(log.toFile());
        return pb.start();
    }

    private static Path bootJar(String service) throws Exception {
        Path primary = Path.of("services", service, "build", "libs").toAbsolutePath().normalize();
        final Path libs = Files.isDirectory(primary) ? primary : primary;
        try (var stream = Files.list(libs)) {
            return stream
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".jar") && !n.endsWith("-plain.jar") && n.startsWith(service);
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("bootJar missing for " + service));
        }
    }

    private static void awaitHealthy(int port) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        URI uri = URI.create("http://127.0.0.1:" + port + "/actuator/health");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> resp = http.send(
                        HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body().contains("UP")) {
                    return;
                }
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Service :" + port + " not healthy", last);
    }

    private static void exec(String schema, String... sql) throws Exception {
        try (var c = DriverManager.getConnection(schemaUrl(schema), USER, PASS);
                var st = c.createStatement()) {
            for (String s : sql) {
                st.executeUpdate(s);
            }
        }
    }

    private static String schemaUrl(String schema) {
        return JDBC.contains("?")
                ? JDBC + "&currentSchema=" + schema
                : JDBC + "?currentSchema=" + schema;
    }
}
