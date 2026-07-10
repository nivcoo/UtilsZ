package fr.nivcoo.utilsz.core.messaging;

import fr.nivcoo.utilsz.core.config.common.ServiceDiscoveryConfig;
import fr.nivcoo.utilsz.core.messaging.discovery.ServiceDiscoveryTicker;
import fr.nivcoo.utilsz.core.scheduler.PluginScheduler;
import fr.nivcoo.utilsz.core.scheduler.ScheduledTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDiscoveryTickerTest {

    private DefaultMessageBus firstBus;
    private DefaultMessageBus secondBus;

    @BeforeEach
    void resetBackend() {
        InMemoryMessageBackend.reset();
    }

    @AfterEach
    void closeBuses() {
        if (firstBus != null) firstBus.close();
        if (secondBus != null) secondBus.close();
        InMemoryMessageBackend.reset();
    }

    @Test
    void discoveryPublishesAndStoresRemoteServices() {
        String channel = "discovery-" + UUID.randomUUID();
        firstBus = bus("first", channel);
        secondBus = bus("second", channel);
        ServiceDiscoveryConfig config = config(true);

        ServiceDiscoveryTicker first = config.createTicker(
                new ImmediateScheduler(),
                firstBus,
                () -> "server-a",
                () -> "cluster-a"
        );
        ServiceDiscoveryTicker second = config.createTicker(
                new ImmediateScheduler(),
                secondBus,
                () -> "server-b",
                () -> "cluster-a"
        );

        second.start();
        first.start();

        Optional<ServiceDiscoveryTicker.Service> remote = second.find("server-a");

        assertTrue(remote.isPresent());
        assertEquals("edenplayers", remote.get().service());
        assertEquals("server-a", remote.get().serverId());
        assertEquals("cluster-a", remote.get().cluster());
        assertTrue(second.isLocalServer("server-b"));
        assertFalse(second.isLocalServer("server-a"));
    }

    @Test
    void discoveryIgnoresOtherClustersWhenConfigured() {
        String channel = "discovery-" + UUID.randomUUID();
        firstBus = bus("first", channel);
        secondBus = bus("second", channel);
        ServiceDiscoveryConfig config = config(true);

        ServiceDiscoveryTicker first = config.createTicker(
                new ImmediateScheduler(),
                firstBus,
                () -> "server-a",
                () -> "cluster-a"
        );
        ServiceDiscoveryTicker second = config.createTicker(
                new ImmediateScheduler(),
                secondBus,
                () -> "server-b",
                () -> "cluster-b"
        );

        second.start();
        first.start();

        assertTrue(second.find("server-a").isEmpty());
    }

    private DefaultMessageBus bus(String instanceId, String channel) {
        DefaultMessageBus bus = new DefaultMessageBus(
                new InMemoryMessageBackend(instanceId),
                channel,
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        bus.start();
        return bus;
    }

    private static ServiceDiscoveryConfig config(boolean sameClusterOnly) {
        ServiceDiscoveryConfig config = new ServiceDiscoveryConfig(true, "edenplayers");
        config.sameClusterOnly = sameClusterOnly;
        config.intervalSeconds = 5;
        config.ttlSeconds = 30;
        return config;
    }

    private static final class ImmediateScheduler implements PluginScheduler {
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
            return cancellable();
        }

        @Override
        public ScheduledTask runRepeatingAsync(Runnable task, long delayTicks, long periodTicks) {
            return cancellable();
        }

        private ScheduledTask cancellable() {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            return () -> cancelled.set(true);
        }
    }
}
