package fr.nivcoo.utilsz.core.config.reload;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.scheduler.PluginScheduler;
import fr.nivcoo.utilsz.core.scheduler.ScheduledTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigReloadTickerTest {

    @Test
    void reloadsAStableCandidateAndKeepsThePreviousValueOnFailure(@TempDir Path directory) throws Exception {
        ConfigManager manager = new ConfigManager(directory.toFile());
        ReloadConfig initial = manager.load("config.yml", ReloadConfig.class);
        TestScheduler scheduler = new TestScheduler();
        AtomicReference<String> applied = new AtomicReference<>(initial.value);
        AtomicInteger failures = new AtomicInteger();
        ConfigReloadTicker<ReloadConfig> ticker = manager.watch(
                "config.yml",
                ReloadConfig.class,
                initial,
                scheduler,
                event -> {
                    if ("invalid".equals(event.candidate().value)) {
                        throw new IllegalStateException("invalid candidate");
                    }
                    applied.set(event.candidate().value);
                },
                ignored -> failures.incrementAndGet(),
                new ConfigReloadOptions(1L, 2, 1L));
        ticker.start();

        Files.writeString(directory.resolve("config.yml"), "value: second\n");
        scheduler.tick();
        assertEquals("first", ticker.current().value);
        scheduler.tick();
        assertEquals("second", ticker.current().value);
        assertEquals("second", applied.get());

        Files.writeString(directory.resolve("config.yml"), "value: invalid\n");
        scheduler.tick();
        scheduler.tick();
        assertEquals("second", ticker.current().value);
        assertEquals("second", applied.get());
        assertEquals(1, failures.get());
    }

    public static final class ReloadConfig {
        public String value = "first";
    }

    @Test
    void rejectsInvalidReloadOptions() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigReloadOptions(0L, 2, 600L));
        assertThrows(IllegalArgumentException.class, () -> new ConfigReloadOptions(20L, 0, 600L));
        assertThrows(IllegalArgumentException.class, () -> new ConfigReloadOptions(20L, 2, 0L));
    }

    private static final class TestScheduler implements PluginScheduler {
        private Runnable repeating;

        void tick() {
            if (repeating != null) repeating.run();
        }

        @Override
        public ScheduledTask run(Runnable task) {
            task.run();
            return ScheduledTask.NOOP;
        }

        @Override
        public ScheduledTask runAsync(Runnable task) {
            task.run();
            return ScheduledTask.NOOP;
        }

        @Override
        public ScheduledTask runLater(Runnable task, long delayTicks) {
            task.run();
            return ScheduledTask.NOOP;
        }

        @Override
        public ScheduledTask runLaterAsync(Runnable task, long delayTicks) {
            task.run();
            return ScheduledTask.NOOP;
        }

        @Override
        public ScheduledTask runRepeating(Runnable task, long delayTicks, long periodTicks) {
            repeating = task;
            return () -> repeating = null;
        }

        @Override
        public ScheduledTask runRepeatingAsync(Runnable task, long delayTicks, long periodTicks) {
            return runRepeating(task, delayTicks, periodTicks);
        }
    }
}
