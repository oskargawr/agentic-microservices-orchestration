package pl.edu.pw.thesis.orchestration.varianta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
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
import pl.edu.pw.thesis.contracts.inventory.ReservationState;
import pl.edu.pw.thesis.contracts.payment.PaymentState;
import pl.edu.pw.thesis.contracts.shipping.ShipmentState;
import pl.edu.pw.thesis.orchestration.varianta.model.BusinessOutcome;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentInput;
import pl.edu.pw.thesis.orchestration.varianta.model.OrderFulfillmentResult;

/**
 * TH-201 compensation scenarios against real Spring Boot services + Postgres + Temporal test env.
 */
@EnabledIf("postgresAvailable")
class OrderFulfillmentCompensationIT {

    static final String JDBC = System.getenv().getOrDefault(
            "VARIANT_A_TEST_JDBC_URL",
            "jdbc:postgresql://localhost:5433/business");
    static final String USER = System.getenv().getOrDefault("VARIANT_A_TEST_USER", "thesis");
    static final String PASS = System.getenv().getOrDefault("VARIANT_A_TEST_PASSWORD", "thesis");

    static final int INV_PORT = 18181;
    static final int PAY_PORT = 18182;
    static final int SHIP_PORT = 18183;

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
        PROCESSES.add(startService("inventory", INV_PORT, "inventory", false));
        PROCESSES.add(startService("payment", PAY_PORT, "payment", true));
        PROCESSES.add(startService("shipping", SHIP_PORT, "shipping", true));
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
    void t201_1_chargeFailReleasesReservation_realServices() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        try (TestWorkflowEnvironment testEnv = newEnv(http)) {
            UUID orderId = UUID.randomUUID();
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    orderId, "SKU-MVP-1", 1, Money.plnMinor(2599), "CHARGE_FAIL", null, null));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.RELEASED);
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.FAILED);
            assertThat(queryOps("payment")).doesNotContain("refund");
            assertThat(queryOps("inventory")).contains("release");
            assertThat(queryOps("shipping")).isEmpty();
            assertThat(indexOf(queryOps("inventory"), "reserve"))
                    .isLessThan(indexOf(queryOps("inventory"), "release"));
        }
    }

    @Test
    void t400_s2_arrangeRejected_refundsAndReleases_noShipment() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        try (TestWorkflowEnvironment testEnv = newEnv(http)) {
            UUID orderId = UUID.randomUUID();
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    orderId,
                    "SKU-MVP-1",
                    1,
                    Money.plnMinor(2599),
                    null,
                    null,
                    "ARRANGE_REJECTED",
                    null));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.REFUNDED);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.RELEASED);
            assertThat(result.shipmentId()).isNull();
            assertThat(countShipments(orderId)).isZero();
            assertThat(queryOps("payment")).contains("refund");
            assertThat(queryOps("inventory")).contains("release");
        }
    }

    @Test
    void t201_2_arrangeFailRefundsThenReleases_realServices() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        RecordingActivities activities = new RecordingActivities(http);
        activities.failArrangeWithBusiness("controlled arrange failure");

        try (TestWorkflowEnvironment testEnv = newEnv(activities)) {
            UUID orderId = UUID.randomUUID();
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    orderId, "SKU-MVP-1", 1, Money.plnMinor(2599)));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.REFUNDED);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.RELEASED);

            List<String> payOps = queryOps("payment");
            List<String> invOps = queryOps("inventory");
            assertThat(payOps).containsSubsequence("charge", "refund");
            assertThat(invOps).containsSubsequence("reserve", "release");
            assertThat(indexOf(payOps, "refund")).isGreaterThan(indexOf(payOps, "charge"));
            // refund before release across services: payment refund timestamp vs inventory release
            assertThat(queryOps("shipping")).isEmpty();
        }
    }

    @Test
    void t201_3_consumeFailCancelsRefundsReleases_realServices() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        RecordingActivities activities = new RecordingActivities(http);
        activities.failConsumeWithBusiness("controlled consume failure");

        try (TestWorkflowEnvironment testEnv = newEnv(activities)) {
            UUID orderId = UUID.randomUUID();
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    orderId, "SKU-MVP-1", 1, Money.plnMinor(2599)));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.COMPENSATED_FAILURE);
            assertThat(http.getShipment(result.shipmentId()).state()).isEqualTo(ShipmentState.CANCELLED);
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.REFUNDED);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.RELEASED);

            List<String> shipOps = queryOps("shipping");
            List<String> payOps = queryOps("payment");
            List<String> invOps = queryOps("inventory");
            assertThat(shipOps).containsSubsequence("arrange", "cancel");
            assertThat(payOps).contains("refund");
            assertThat(invOps).contains("release");
            assertThat(result.compensationLog()).containsExactly("cancel", "refund", "release");
        }
    }

    @Test
    void t201_4_unknownResolvesToCaptured_realServices() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        try (TestWorkflowEnvironment testEnv = newEnv(http)) {
            UUID orderId = UUID.randomUUID();
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    orderId, "SKU-MVP-1", 1, Money.plnMinor(2599),
                    "CHARGE_UNKNOWN", "RESOLVE_CAPTURE", 2));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.SUCCESS);
            assertThat(result.matchesConfirmedSuccessSnapshot()).isTrue();
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.CONSUMED);
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.CAPTURED);
            assertThat(http.getShipment(result.shipmentId()).state()).isEqualTo(ShipmentState.ARRANGED);
            assertThat(queryOps("payment")).doesNotContain("refund");
            assertThat(queryOps("inventory")).contains("consume").doesNotContain("release");
        }
    }

    @Test
    void t201_5_unknownRemainsAmbiguous_realServices() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        try (TestWorkflowEnvironment testEnv = newEnv(http)) {
            UUID orderId = UUID.randomUUID();
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    orderId, "SKU-MVP-1", 1, Money.plnMinor(2599),
                    "CHARGE_UNKNOWN", "RESOLVE_INCONCLUSIVE", 2));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.UNKNOWN);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.RELEASED);
            assertThat(queryOps("shipping")).isEmpty();
            assertThat(queryOps("payment")).doesNotContain("refund");
            assertThat(queryOps("inventory")).contains("release");
        }
    }

    @Test
    void f3Recovered_chargeLostThenResolveExposesCaptured_success() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        try (TestWorkflowEnvironment testEnv = newEnv(http)) {
            UUID orderId = UUID.randomUUID();
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    orderId, "SKU-MVP-1", 1, Money.plnMinor(2599),
                    "F3_CAPTURE_LOST_RESPONSE", null, 2));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.SUCCESS);
            assertThat(result.matchesConfirmedSuccessSnapshot()).isTrue();
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.CAPTURED);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.CONSUMED);
            assertThat(http.getShipment(result.shipmentId()).state()).isEqualTo(ShipmentState.ARRANGED);
            assertThat(queryOps("payment")).doesNotContain("refund");
            assertThat(queryOps("shipping")).isNotEmpty();
        }
    }

    @Test
    void f3Unresolved_opacityLeavesCapturedHeld_noRefundNoArrange() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        try (TestWorkflowEnvironment testEnv = newEnv(http)) {
            UUID orderId = UUID.randomUUID();
            // resolveAttemptLimit=1 keeps Temporal 503 retry budget bounded for the test env
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    orderId, "SKU-MVP-1", 1, Money.plnMinor(2599),
                    "F3_CAPTURE_LOST_RESPONSE", "RESOLVE_TRANSPORT_OPAQUE", 1));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
            assertThat(result.paymentState()).isEqualTo(PaymentState.CAPTURED);
            assertThat(result.reservationState()).isEqualTo(ReservationState.HELD);
            assertThat(result.shipmentId()).isNull();
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.CAPTURED);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.HELD);
            assertThat(queryOps("shipping")).isEmpty();
            assertThat(queryOps("payment")).contains("charge").doesNotContain("refund");
            assertThat(queryOps("inventory")).contains("reserve").doesNotContain("release");
            assertThat(result.compensationLog())
                    .contains("refund-skipped:experimental-opacity-unconfirmed")
                    .contains("release-skipped:experimental-opacity-unconfirmed");
        }
    }

    @Test
    void f3Unresolved_businessResolveFailure_doesNotLeaveDangling() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        RecordingActivities activities = new RecordingActivities(http);
        activities.failResolveWithBusiness("controlled BUSINESS resolve failure");
        try (TestWorkflowEnvironment testEnv = newEnv(activities)) {
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599),
                    "F3_CAPTURE_LOST_RESPONSE", "RESOLVE_TRANSPORT_OPAQUE", 1));

            assertThat(result.compensationLog())
                    .doesNotContain("experimental-opacity-unconfirmed");
            assertThat(queryOps("payment")).contains("refund");
            assertThat(queryOps("inventory")).contains("release");
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.REFUNDED);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.RELEASED);
        }
    }

    @Test
    void f3Unresolved_unrelatedHttp500Resolve_doesNotLeaveDangling() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        RecordingActivities activities = new RecordingActivities(http);
        activities.failResolveWithHttp500("controlled unrelated HTTP_500");
        try (TestWorkflowEnvironment testEnv = newEnv(activities)) {
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599),
                    "F3_CAPTURE_LOST_RESPONSE", "RESOLVE_TRANSPORT_OPAQUE", 1));

            assertThat(result.compensationLog())
                    .doesNotContain("experimental-opacity-unconfirmed");
            assertThat(queryOps("payment")).contains("refund");
            assertThat(queryOps("inventory")).contains("release");
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.REFUNDED);
        }
    }

    @Test
    void f3Unresolved_exhaustedHttp503Override_stillLeavesDangling() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        RecordingActivities activities = new RecordingActivities(http);
        // Force ApplicationFailure type HTTP_503 (same as HttpOrderFulfillmentActivities mapping)
        activities.failResolveAlwaysHttp503("forced opacity-equivalent 503");
        try (TestWorkflowEnvironment testEnv = newEnv(activities)) {
            OrderFulfillmentResult result = stub(testEnv).fulfill(new OrderFulfillmentInput(
                    UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599),
                    "F3_CAPTURE_LOST_RESPONSE", "RESOLVE_TRANSPORT_OPAQUE", 1));

            assertThat(result.outcome()).isEqualTo(BusinessOutcome.AMBIGUOUS);
            assertThat(result.compensationLog())
                    .contains("refund-skipped:experimental-opacity-unconfirmed")
                    .contains("release-skipped:experimental-opacity-unconfirmed");
            assertThat(queryOps("payment")).contains("charge").doesNotContain("refund");
            assertThat(queryOps("inventory")).contains("reserve").doesNotContain("release");
            assertThat(http.getPayment(result.paymentId(), null).state()).isEqualTo(PaymentState.CAPTURED);
            assertThat(http.getReservation(result.reservationId()).state())
                    .isEqualTo(ReservationState.HELD);
        }
    }

    @Test
    void t201_6_incompleteCompensation_realCancelThenRefundFails() throws Exception {
        HttpOrderFulfillmentActivities http = http();
        RecordingActivities activities = new RecordingActivities(http);
        activities.failConsumeWithBusiness("controlled consume failure");
        activities.failRefundWithBusiness("controlled refund failure");

        try (TestWorkflowEnvironment testEnv = newEnv(activities)) {
            assertThatThrownBy(() -> stub(testEnv).fulfill(new OrderFulfillmentInput(
                            UUID.randomUUID(), "SKU-MVP-1", 1, Money.plnMinor(2599))))
                    .isInstanceOf(WorkflowFailedException.class)
                    .satisfies(ex -> assertThat(rootApplicationFailure(ex).getType())
                            .isEqualTo(ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE));

            // Cancel succeeded in shipping; refund failed; release must not claim full compensation
            assertThat(queryOps("shipping")).containsSubsequence("arrange", "cancel");
            assertThat(queryOps("payment")).contains("charge").doesNotContain("refund");
            assertThat(queryOps("inventory")).doesNotContain("release");
        }
    }

    private static ApplicationFailure rootApplicationFailure(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        assertThat(cur).isInstanceOf(ApplicationFailure.class);
        return (ApplicationFailure) cur;
    }

    private static HttpOrderFulfillmentActivities http() {
        return new HttpOrderFulfillmentActivities(new ServiceEndpoints(
                "http://localhost:" + INV_PORT,
                "http://localhost:" + PAY_PORT,
                "http://localhost:" + SHIP_PORT));
    }

    private static TestWorkflowEnvironment newEnv(OrderFulfillmentActivities activities) {
        TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(OrderFulfillmentWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        testEnv.start();
        return testEnv;
    }

    private static OrderFulfillmentWorkflow stub(TestWorkflowEnvironment testEnv) {
        return testEnv.getWorkflowClient().newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(OrderFulfillmentWorkflow.TASK_QUEUE)
                        .setWorkflowId("th201-" + UUID.randomUUID())
                        .build());
    }

    private static Process startService(String name, int port, String schema, boolean enableFaultInjection)
            throws Exception {
        Path jar = bootJar(name);
        String url = schemaUrl(schema);
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> cmd = new ArrayList<>(List.of(
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
                "--logging.level.root=WARN"));
        if (enableFaultInjection && "payment".equals(name)) {
            cmd.add("--thesis.payment.fault-injection-enabled=true");
        }
        if (enableFaultInjection && "shipping".equals(name)) {
            cmd.add("--thesis.shipping.fault-injection-enabled=true");
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Path log = Files.createTempFile("th201-" + name + "-", ".log");
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
                            "bootJar not found for " + service + " under " + libs));
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

    private static int countShipments(UUID orderId) throws Exception {
        try (Connection c = DriverManager.getConnection(schemaUrl("shipping"), USER, PASS);
                var ps = c.prepareStatement("SELECT COUNT(*) FROM shipments WHERE order_id = ?")) {
            ps.setObject(1, orderId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static List<String> queryOps(String schema) throws Exception {
        List<String> ops = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(schemaUrl(schema), USER, PASS);
                Statement st = c.createStatement();
                var rs = st.executeQuery("SELECT operation FROM operation_log ORDER BY id")) {
            while (rs.next()) {
                ops.add(rs.getString(1));
            }
        }
        return ops;
    }

    private static int indexOf(List<String> ops, String name) {
        int i = ops.indexOf(name);
        assertThat(i).as("expected op %s in %s", name, ops).isGreaterThanOrEqualTo(0);
        return i;
    }

    private static String schemaUrl(String schema) {
        return JDBC.contains("?")
                ? JDBC + "&currentSchema=" + schema
                : JDBC + "?currentSchema=" + schema;
    }
}
