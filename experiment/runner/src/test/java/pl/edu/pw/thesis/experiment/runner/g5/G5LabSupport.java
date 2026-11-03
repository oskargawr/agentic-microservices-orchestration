package pl.edu.pw.thesis.experiment.runner.g5;

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
import java.util.concurrent.TimeUnit;
import pl.edu.pw.thesis.orchestration.varianta.ServiceEndpoints;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalClients;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;
import pl.edu.pw.thesis.orchestration.varianta.runtime.VariantAWorkers;

/**
 * Test/lab process helpers for opt-in G5 live IT. Not used by production runner code.
 */
public final class G5LabSupport {

    public static final String JDBC = System.getenv()
            .getOrDefault("VARIANT_A_TEST_JDBC_URL", "jdbc:postgresql://localhost:5433/business");
    public static final String USER = System.getenv().getOrDefault("VARIANT_A_TEST_USER", "thesis");
    public static final String PASS =
            System.getenv().getOrDefault("VARIANT_A_TEST_PASSWORD", "thesis");
    public static final String TEMPORAL_TARGET =
            System.getenv().getOrDefault("TEMPORAL_TARGET", "localhost:7233");
    public static final String TEMPORAL_NAMESPACE =
            System.getenv().getOrDefault("TEMPORAL_NAMESPACE", "default");

    /** Ports distinct from ConsoleRealTemporalE2EIT (19081–3). */
    public static final int INV_PORT = 19181;

    public static final int PAY_PORT = 19182;
    public static final int SHIP_PORT = 19183;

    private G5LabSupport() {}

    public static boolean postgresAvailable() {
        try (var c = DriverManager.getConnection(JDBC, USER, PASS)) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean temporalAvailable() {
        WorkflowServiceStubs stubs = null;
        try {
            TemporalSettings settings = new TemporalSettings(TEMPORAL_TARGET, TEMPORAL_NAMESPACE);
            stubs = TemporalClients.newServiceStubs(settings);
            stubs.blockingStub()
                    .getSystemInfo(
                            io.temporal.api.workflowservice.v1.GetSystemInfoRequest.newBuilder()
                                    .build());
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (stubs != null) {
                stubs.shutdown();
            }
        }
    }

    public static boolean dedicatedEnvironmentConfirmed() {
        return G5OptIn.isTruthy(System.getenv("THESIS_G5_DEDICATED_ENV"));
    }

    public static Process startService(String name, int port, String schema) throws Exception {
        Path jar = bootJar(name);
        String url = schemaUrl(schema);
        String java = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(
                java,
                "-jar",
                jar.toAbsolutePath().toString(),
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
        Path log = Files.createTempFile("g5-" + name + "-", ".log");
        pb.redirectOutput(log.toFile());
        return pb.start();
    }

    public static void awaitHealthy(int port) throws Exception {
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

    public static WorkerBundle startWorker() {
        TemporalSettings settings = new TemporalSettings(TEMPORAL_TARGET, TEMPORAL_NAMESPACE);
        WorkflowServiceStubs stubs = TemporalClients.newServiceStubs(settings);
        WorkflowClient client = TemporalClients.newWorkflowClient(stubs, settings);
        WorkerFactory factory = VariantAWorkers.startWorkerFactory(
                client,
                new ServiceEndpoints(
                        "http://127.0.0.1:" + INV_PORT,
                        "http://127.0.0.1:" + PAY_PORT,
                        "http://127.0.0.1:" + SHIP_PORT));
        return new WorkerBundle(factory, stubs);
    }

    public static void stopProcesses(List<Process> processes) {
        for (Process p : processes) {
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
        processes.clear();
    }

    public static Path bootJar(String service) throws Exception {
        Path libs = Path.of("services", service, "build", "libs").toAbsolutePath().normalize();
        try (var stream = Files.list(libs)) {
            return stream.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".jar")
                                && !n.endsWith("-plain.jar")
                                && n.startsWith(service);
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("bootJar missing for " + service));
        }
    }

    public static String schemaUrl(String schema) {
        return JDBC.contains("?")
                ? JDBC + "&currentSchema=" + schema
                : JDBC + "?currentSchema=" + schema;
    }

    public record WorkerBundle(WorkerFactory factory, WorkflowServiceStubs stubs) {
        public void stop() {
            if (factory != null) {
                factory.shutdown();
            }
            if (stubs != null) {
                stubs.shutdown();
            }
        }
    }

    /** Mutable holder for G5-2 stack lifecycle. */
    public static final class ServiceStack implements AutoCloseable {
        private final List<Process> processes = new ArrayList<>();
        private WorkerBundle worker;

        public void startServices() throws Exception {
            processes.add(startService("inventory", INV_PORT, "inventory"));
            processes.add(startService("payment", PAY_PORT, "payment"));
            processes.add(startService("shipping", SHIP_PORT, "shipping"));
            awaitHealthy(INV_PORT);
            awaitHealthy(PAY_PORT);
            awaitHealthy(SHIP_PORT);
        }

        public void startWorkerPolling() {
            if (worker != null) {
                throw new IllegalStateException("worker already started");
            }
            worker = startWorker();
        }

        public void stopWorkerOnly() {
            if (worker != null) {
                worker.stop();
                worker = null;
            }
        }

        @Override
        public void close() {
            stopWorkerOnly();
            stopProcesses(processes);
        }
    }
}
