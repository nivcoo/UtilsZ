package fr.nivcoo.utilsz.core.config.reload;

import fr.nivcoo.utilsz.core.scheduler.PluginScheduler;
import fr.nivcoo.utilsz.core.scheduler.ScheduledTask;
import fr.nivcoo.utilsz.core.ticker.PluginTicker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ConfigReloadTicker<T> implements PluginTicker {

    private final PluginScheduler scheduler;
    private final Path file;
    private final Supplier<T> loader;
    private final ConfigReloadListener<T> listener;
    private final Consumer<Throwable> errorHandler;
    private final long pollTicks;
    private final int stablePolls;
    private final long retryNanos;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile ScheduledTask task = ScheduledTask.NOOP;
    private volatile T current;
    private volatile String appliedFingerprint;
    private volatile String observedFingerprint;
    private volatile int observations;
    private volatile String failedFingerprint;
    private volatile long retryAfter;

    public ConfigReloadTicker(PluginScheduler scheduler, Path file, T current, Supplier<T> loader,
                              ConfigReloadListener<T> listener, Consumer<Throwable> errorHandler,
                              ConfigReloadOptions options) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.current = Objects.requireNonNull(current, "current");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        Objects.requireNonNull(options, "options");
        this.pollTicks = options.pollTicks();
        this.stablePolls = options.stablePolls();
        this.retryNanos = Math.multiplyExact(options.retryTicks(), 50_000_000L);
        try {
            this.appliedFingerprint = Objects.requireNonNull(fingerprint(this.file),
                    "Auto-reloaded config file does not exist");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint config file: " + this.file, exception);
        }
    }

    public T current() {
        return current;
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) return;
        task = scheduler.runRepeatingAsync(this::poll, pollTicks, pollTicks);
    }

    @Override
    public void stop() {
        started.set(false);
        task.cancel();
        task = ScheduledTask.NOOP;
    }

    void poll() {
        if (!started.get()) return;
        String fingerprint;
        try {
            fingerprint = fingerprint(file);
        } catch (IOException exception) {
            errorHandler.accept(exception);
            return;
        }
        if (fingerprint == null || fingerprint.equals(appliedFingerprint)) {
            resetObservation();
            failedFingerprint = null;
            return;
        }
        if (!fingerprint.equals(observedFingerprint)) {
            observedFingerprint = fingerprint;
            observations = 1;
            if (observations < stablePolls) return;
        } else {
            observations++;
            if (observations < stablePolls) return;
        }
        if (fingerprint.equals(failedFingerprint) && System.nanoTime() < retryAfter) return;
        if (!loading.compareAndSet(false, true)) return;
        try {
            String expectedFingerprint = fingerprint;
            T candidate = Objects.requireNonNull(loader.get(), "config loader returned null");
            String candidateFingerprint = Objects.requireNonNull(fingerprint(file),
                    "Auto-reloaded config file disappeared");
            if (!candidateFingerprint.equals(expectedFingerprint)) {
                observedFingerprint = candidateFingerprint;
                observations = 1;
                failedFingerprint = null;
                return;
            }
            if (!started.get()) return;
            listener.reload(new ConfigReloadEvent<>(file, current, candidate));
            if (!started.get()) return;
            current = candidate;
            appliedFingerprint = candidateFingerprint;
            resetObservation();
            failedFingerprint = null;
        } catch (Exception exception) {
            failedFingerprint = currentFingerprint(fingerprint);
            observedFingerprint = failedFingerprint;
            observations = stablePolls;
            retryAfter = System.nanoTime() + retryNanos;
            if (started.get()) errorHandler.accept(exception);
        } finally {
            loading.set(false);
        }
    }

    private String currentFingerprint(String fallback) {
        try {
            String fingerprint = fingerprint(file);
            return fingerprint == null ? fallback : fingerprint;
        } catch (IOException ignored) {
            return fallback;
        }
    }

    private void resetObservation() {
        observedFingerprint = null;
        observations = 0;
    }

    static String fingerprint(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
