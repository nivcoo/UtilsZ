package fr.nivcoo.utilsz.core.messaging.discovery;

import fr.nivcoo.utilsz.core.config.common.ServiceDiscoveryConfig;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;
import fr.nivcoo.utilsz.core.messaging.MessageBus;
import fr.nivcoo.utilsz.core.scheduler.PluginScheduler;
import fr.nivcoo.utilsz.core.scheduler.ScheduledTask;
import fr.nivcoo.utilsz.core.ticker.PluginTicker;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ServiceDiscoveryTicker implements PluginTicker {

    private final PluginScheduler scheduler;
    private final MessageBus bus;
    private final ServiceDiscoveryConfig config;
    private final Supplier<String> serverIdSupplier;
    private final Supplier<String> clusterSupplier;
    private final Map<String, Service> byServerId = new ConcurrentHashMap<>();

    private volatile String localServerId = "server-unknown";
    private volatile String localCluster = "global";
    private ScheduledTask heartbeatTask = ScheduledTask.NOOP;

    public ServiceDiscoveryTicker(PluginScheduler scheduler,
                                  MessageBus bus,
                                  ServiceDiscoveryConfig config,
                                  Supplier<String> serverIdSupplier,
                                  Supplier<String> clusterSupplier) {

        this.scheduler = scheduler;
        this.bus = bus;
        this.config = config;
        this.serverIdSupplier = serverIdSupplier;
        this.clusterSupplier = clusterSupplier;
        register();
    }

    @Override
    public void start() {
        stop();
        refreshLocal();
        if (scheduler == null || config == null || !config.enabled) return;

        long periodTicks = config.intervalSeconds() * 20L;
        heartbeatTask = scheduler.runRepeatingAsync(this::publishLocal, 20L, periodTicks);
        scheduler.runAsync(this::publishLocal);
    }

    @Override
    public void stop() {
        heartbeatTask.cancel();
        heartbeatTask = ScheduledTask.NOOP;
        clear();
    }

    public Optional<Service> find(String serverId) {
        pruneExpired();
        Service service = byServerId.get(Service.normalizeServer(serverId));
        return service == null ? Optional.empty() : Optional.of(service);
    }

    public List<Service> services() {
        pruneExpired();
        return List.copyOf(byServerId.values());
    }

    public boolean isLocalServer(String serverId) {
        return localServerId.equals(Service.normalizeServer(serverId));
    }

    public String localServerId() {
        return localServerId;
    }

    public String localCluster() {
        return localCluster;
    }

    public void clear() {
        byServerId.clear();
    }

    private void register() {
        if (bus == null) return;
        bus.register(Heartbeat.class, this::apply);
    }

    private Service refreshLocal() {
        Service service = createLocalService();
        store(service);
        return service;
    }

    private void publishLocal() {
        if (bus == null) return;
        Service service = refreshLocal();
        bus.publish(new Heartbeat(
                service.service(),
                service.serverId(),
                service.cluster(),
                service.instanceId(),
                service.sentAt(),
                service.expiresAt()
        ));
    }

    private void apply(Heartbeat heartbeat) {
        if (heartbeat == null) return;
        store(new Service(
                heartbeat.service(),
                heartbeat.serverId(),
                heartbeat.cluster(),
                heartbeat.instanceId(),
                heartbeat.sentAt(),
                heartbeat.expiresAt()
        ));
    }

    private Service createLocalService() {
        localServerId = Service.normalizeServer(get(serverIdSupplier, "server-unknown"));
        localCluster = Service.normalizeCluster(get(clusterSupplier, "global"));
        long now = System.currentTimeMillis();
        long ttlMillis = config == null ? 15_000L : config.ttlMillis();
        return new Service(
                Service.normalizeService(config == null ? "plugin" : config.service),
                localServerId,
                localCluster,
                bus == null ? "" : bus.instanceId(),
                now,
                now + ttlMillis
        );
    }

    private void store(Service service) {
        long now = System.currentTimeMillis();
        if (service == null || service.isExpired(now)) return;
        if (!Service.normalizeService(config == null ? "plugin" : config.service).equals(service.service())) return;
        if (service.instanceId() == null || service.instanceId().isBlank()) return;
        if (config != null && config.sameClusterOnly && !localCluster.equals(service.cluster())) return;

        byServerId.compute(service.serverId(), (ignored, current) -> {
            if (current != null && current.sentAt() > service.sentAt()) return current;
            return service;
        });
        pruneExpired();
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        byServerId.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static String get(Supplier<String> supplier, String fallback) {
        if (supplier == null) return fallback;
        String value = supplier.get();
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Service(
            String service,
            String serverId,
            String cluster,
            String instanceId,
            long sentAt,
            long expiresAt
    ) {

        public Service {
            service = normalizeService(service);
            serverId = normalizeServer(serverId);
            cluster = normalizeCluster(cluster);
            instanceId = instanceId == null ? "" : instanceId.trim();
        }

        public boolean isExpired(long now) {
            return expiresAt <= now;
        }

        public static String normalizeService(String service) {
            return service == null || service.isBlank()
                    ? "default"
                    : service.trim().toLowerCase(Locale.ROOT);
        }

        public static String normalizeServer(String serverId) {
            return serverId == null || serverId.isBlank()
                    ? "server-unknown"
                    : serverId.trim().toLowerCase(Locale.ROOT);
        }

        public static String normalizeCluster(String cluster) {
            return cluster == null || cluster.isBlank()
                    ? "global"
                    : cluster.trim().toLowerCase(Locale.ROOT);
        }
    }

    @BusAction("utilsz:service_discovery_heartbeat")
    private record Heartbeat(
            String service,
            String serverId,
            String cluster,
            String instanceId,
            long sentAt,
            long expiresAt
    ) implements BusMessage {

        @Override
        public void execute() {
        }
    }
}
