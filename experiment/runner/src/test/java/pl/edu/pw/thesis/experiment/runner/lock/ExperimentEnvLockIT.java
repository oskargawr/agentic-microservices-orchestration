package pl.edu.pw.thesis.experiment.runner.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.edu.pw.thesis.experiment.runner.config.UnsafeEnvironmentException;

/** G3 — exclusive env lock: second holder receives ENV_BUSY (cross-process via subprocess). */
class ExperimentEnvLockIT {

    @TempDir Path tmp;

    @Test
    void sameJvmSecondAcquireReceivesEnvBusy() throws Exception {
        try (ExperimentEnvLock first = ExperimentEnvLock.acquire(tmp, "lab-g3")) {
            assertThat(first.lockFile()).exists();
            assertThatThrownBy(() -> ExperimentEnvLock.acquire(tmp, "lab-g3"))
                    .isInstanceOf(UnsafeEnvironmentException.class)
                    .extracting(ex -> ((UnsafeEnvironmentException) ex).exclusionReason())
                    .isEqualTo("ENV_BUSY");
        }
    }

    @Test
    void crossProcessSecondAcquireReceivesEnvBusy() throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = Path.of(javaHome, "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        try (ExperimentEnvLock held = ExperimentEnvLock.acquire(tmp, "lab-g3-proc")) {
            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add(LockProbeMain.class.getName());
            cmd.add(tmp.toAbsolutePath().toString());
            cmd.add("lab-g3-proc");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            assertThat(finished).as("lock probe process finished").isTrue();
            String output = new String(process.getInputStream().readAllBytes());
            assertThat(process.exitValue())
                    .as("probe output=%s", output)
                    .isEqualTo(LockProbeMain.EXIT_ENV_BUSY);
            assertThat(held.lockFile()).exists();
        }
    }

    @Test
    void concurrentThreadsSecondAcquireEnvBusy() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<UnsafeEnvironmentException> failure = new AtomicReference<>();

        Thread holder = new Thread(() -> {
            try (ExperimentEnvLock lock = ExperimentEnvLock.acquire(tmp, "lab-g3-thr")) {
                held.countDown();
                release.await(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        holder.start();
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();

        Thread contender = new Thread(() -> {
            try {
                ExperimentEnvLock.acquire(tmp, "lab-g3-thr");
            } catch (UnsafeEnvironmentException e) {
                failure.set(e);
            }
        });
        contender.start();
        contender.join(10_000);
        release.countDown();
        holder.join(10_000);

        assertThat(failure.get()).isNotNull();
        assertThat(failure.get().exclusionReason()).isEqualTo("ENV_BUSY");
    }

    /** Subprocess entry — must stay public for ProcessBuilder. */
    public static final class LockProbeMain {
        static final int EXIT_ENV_BUSY = 7;
        static final int EXIT_OK = 0;
        static final int EXIT_OTHER = 1;

        public static void main(String[] args) {
            try {
                Path root = Path.of(args[0]);
                String envId = args[1];
                ExperimentEnvLock.acquire(root, envId).close();
                System.exit(EXIT_OK);
            } catch (UnsafeEnvironmentException e) {
                if ("ENV_BUSY".equals(e.exclusionReason())) {
                    System.out.println("ENV_BUSY");
                    System.exit(EXIT_ENV_BUSY);
                }
                System.out.println(e.getMessage());
                System.exit(EXIT_OTHER);
            } catch (Exception e) {
                e.printStackTrace(System.out);
                System.exit(EXIT_OTHER);
            }
        }
    }
}
