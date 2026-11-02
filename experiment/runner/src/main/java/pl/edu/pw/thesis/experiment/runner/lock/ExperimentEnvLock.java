package pl.edu.pw.thesis.experiment.runner.lock;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import pl.edu.pw.thesis.experiment.runner.config.UnsafeEnvironmentException;

/**
 * Exclusive lock per experimental env (D-R7). Combines an in-process mutex (FileLock is
 * JVM-wide and does not exclude sibling threads/channels) with an OS file lock for
 * cross-process exclusion.
 */
public final class ExperimentEnvLock implements AutoCloseable {

    private static final ConcurrentHashMap<String, Boolean> PROCESS_HELD = new ConcurrentHashMap<>();

    private final Path lockFile;
    private final String processKey;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final FileLock lock;

    private ExperimentEnvLock(
            Path lockFile,
            String processKey,
            RandomAccessFile raf,
            FileChannel channel,
            FileLock lock) {
        this.lockFile = lockFile;
        this.processKey = processKey;
        this.raf = raf;
        this.channel = channel;
        this.lock = lock;
    }

    public static ExperimentEnvLock acquire(Path resultsRoot, String envId) {
        Objects.requireNonNull(resultsRoot, "resultsRoot");
        Objects.requireNonNull(envId, "envId");
        String processKey = resultsRoot.toAbsolutePath().normalize() + "::" + envId;
        if (PROCESS_HELD.putIfAbsent(processKey, Boolean.TRUE) != null) {
            throw new UnsafeEnvironmentException(
                    "ENV_BUSY",
                    "Another Runner in this process holds the exclusive lock for envId=" + envId);
        }
        try {
            Path lockDir = resultsRoot.resolve(".locks");
            Files.createDirectories(lockDir);
            Path lockPath = lockDir.resolve(envId + ".lock");
            RandomAccessFile raf = new RandomAccessFile(lockPath.toFile(), "rw");
            FileChannel channel = raf.getChannel();
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                raf.close();
                PROCESS_HELD.remove(processKey);
                throw new UnsafeEnvironmentException(
                        "ENV_BUSY",
                        "Another Runner holds the exclusive lock for envId=" + envId);
            }
            return new ExperimentEnvLock(lockPath, processKey, raf, channel, lock);
        } catch (UnsafeEnvironmentException e) {
            PROCESS_HELD.remove(processKey);
            throw e;
        } catch (IOException e) {
            PROCESS_HELD.remove(processKey);
            throw new UnsafeEnvironmentException(
                    "ENV_BUSY", "Failed to acquire env lock: " + e.getMessage());
        }
    }

    public Path lockFile() {
        return lockFile;
    }

    @Override
    public void close() {
        try {
            lock.release();
        } catch (IOException ignored) {
            // best-effort
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // best-effort
        }
        try {
            raf.close();
        } catch (IOException ignored) {
            // best-effort
        }
        PROCESS_HELD.remove(processKey);
    }
}
