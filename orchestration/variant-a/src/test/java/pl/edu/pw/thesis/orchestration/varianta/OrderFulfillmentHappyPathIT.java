package pl.edu.pw.thesis.orchestration.varianta;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
import pl.edu.pw.thesis.contracts.common.Money;
import pl.edu.pw.thesis.contracts.inventory.ReservationSnapshot;
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.payment.PaymentSnapshot;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentSnapshot;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

/**
 * Temporal test environment + real Spring Boot service processes over HTTP + Postgres oracle.
 * Services run as separate JVMs (bootJar) so Flyway migrations do not collide on one classpath.
 */
@EnabledIf("postgresAvailable")
class OrderFulfillmentHappyPathIT {

    static final String JDBC = System.getenv().getOrDefault(
            "VARIANT_A_TEST_JDBC_URL",
            "jdbc:postgresql://localhost:5433/business");
    static final String USER = System.getenv().getOrDefault("VARIANT_A_TEST_USER", "thesis");
    static final String PASS = System.getenv().getOrDefault("VARIANT_A_TEST_PASSWORD", "thesis");

    static final int INV_PORT = 18081;
    static final int PAY_PORT = 18082;
    static final int SHIP_PORT = 18083;

    static final List<Process> PROCESSES = new ArrayList<>();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    static boolean postgresAvailable() {
        try (var c = DriverManager.getConnection(JDBC, USER, PASS)) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void startServices() throws Exception {
        PROCESSES.add(startService("inventory", INV_PORT, "inventory"));
        PROCESSES.add(startService("payment", PAY_PORT, "payment"));
        PROCESSES.add(startService("shipping", SHIP_PORT, "shipping"));
        awaitHealthy(INV_PORT);
        awaitHealthy(PAY_PORT);
        awaitHealthy(SHIP_PORT);
    }

    @AfterAll
    static void stopServices() {
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

    @BeforeEach
    void resetBusinessData() throws Exception {
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
    void happyPathPersistsConfirmedSuccessAcrossServices() throws Exception {
        ServiceEndpoints endpoints = new ServiceEndpoints(
                "http://localhost:" + INV_PORT,
                "http://localhost:" + PAY_PORT,
                "http://localhost:" + SHIP_PORT);
        HttpOrderFulfillmentActivities http = new HttpOrderFulfillmentActivities(endpoints);
        RecordingActivities activities = new RecordingActivities(http);

        try (TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance()) {
            WorkflowClient client = testEnv.getWorkflowClient();
            Worker worker = testEnv.newWorker(OrderFulfillmentWorkflow.TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
            testEnv.start();

            UUID orderId = UUID.randomUUID();
            OrderFulfillmentWorkflow workflow = client.newWorkflowStub(
                    OrderFulfillmentWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(OrderFulfillmentWorkflow.TASK_QUEUE)
                            .setWorkflowId("th200-" + orderId)
                            .build());

            OrderFulfillmentResult result = workflow.fulfill(new OrderFulfillmentInput(
                    orderId, "SKU-MVP-1", 1, Money.plnMinor(2599)));

            assertThat(activities.operations())
                    .containsExactly("reserve", "charge", "arrange", "consume");
            assertThat(result.matchesConfirmedSuccessSnapshot()).isTrue();

            ReservationSnapshot reservation = http.getReservation(result.reservationId());
            PaymentSnapshot payment = http.getPayment(result.paymentId(), null);
            ShipmentSnapshot shipment = http.getShipment(result.shipmentId());

            assertThat(reservation.state()).isEqualTo(ReservationState.CONSUMED);
            assertThat(reservation.orderId()).isEqualTo(orderId);
            assertThat(payment.state()).isEqualTo(PaymentState.CAPTURED);
            assertThat(payment.orderId()).isEqualTo(orderId);
            assertThat(shipment.state()).isEqualTo(ShipmentState.ARRANGED);
            assertThat(shipment.orderId()).isEqualTo(orderId);

            assertThat(result.reservationState()).isEqualTo(reservation.state());
            assertThat(result.paymentState()).isEqualTo(payment.state());
            assertThat(result.shipmentState()).isEqualTo(shipment.state());

            assertThat(queryInt("inventory", "SELECT on_hand FROM stock WHERE sku = 'SKU-MVP-1'"))
                    .isEqualTo(99);
            assertThat(queryInt("inventory", "SELECT held FROM stock WHERE sku = 'SKU-MVP-1'"))
                    .isEqualTo(0);
        }
    }

    private static Process startService(String name, int port, String schema) throws Exception {
        Path jar = bootJar(name);
        String url = schemaUrl(schema);
        String java = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(
                java,
                "-jar", jar.toAbsolutePath().toString(),
                "--server.port=" + port,
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + USER,
                "--spring.datasource.password=" + PASS,
                "--spring.flyway.schemas=" + schema,
                "--spring.flyway.default-schema=" + schema,
                "--spring.datasource.hikari.maximum-pool-size=3",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN");
        pb.redirectErrorStream(true);
        Path log = Files.createTempFile("th200-" + name + "-", ".log");
        pb.redirectOutput(log.toFile());
        Process process = pb.start();
        if (!process.isAlive()) {
            throw new IllegalStateException(name + " failed to start; log=" + log + "\n" + Files.readString(log));
        }
        return process;
    }

    private static Path bootJar(String service) throws Exception {
        Path primary = Path.of("services", service, "build", "libs").toAbsolutePath().normalize();
        Path fallback = Path.of("..", "..", "services", service, "build", "libs").toAbsolutePath().normalize();
        final Path libs = Files.isDirectory(primary) ? primary : fallback;
        try (var stream = Files.list(libs)) {
            return stream
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".jar") && !n.endsWith("-plain.jar") && n.startsWith(service);
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "bootJar not found for " + service + " under " + libs
                                    + " — run :services:" + service + ":bootJar first"));
        }
    }

    private static void awaitHealthy(int port) throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/actuator/health");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> resp = HTTP.send(
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
        throw new IllegalStateException("Service on port " + port + " not healthy", last);
    }

    private static void exec(String schema, String... sql) throws Exception {
        try (Connection c = DriverManager.getConnection(schemaUrl(schema), USER, PASS);
                Statement st = c.createStatement()) {
            for (String s : sql) {
                st.executeUpdate(s);
            }
        }
    }

    private static int queryInt(String schema, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(schemaUrl(schema), USER, PASS);
                Statement st = c.createStatement();
                var rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private static String schemaUrl(String schema) {
        return JDBC.contains("?")
                ? JDBC + "&currentSchema=" + schema
                : JDBC + "?currentSchema=" + schema;
    }
}
