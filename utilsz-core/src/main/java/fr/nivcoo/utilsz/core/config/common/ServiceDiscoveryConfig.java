package fr.nivcoo.utilsz.core.config.common;

import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Section;
import fr.nivcoo.utilsz.core.messaging.MessageBus;
import fr.nivcoo.utilsz.core.messaging.discovery.ServiceDiscoveryTicker;
import fr.nivcoo.utilsz.core.scheduler.PluginScheduler;

import java.util.function.Supplier;

@Section
@SuppressWarnings("unused")
public class ServiceDiscoveryConfig {

    public boolean enabled = true;
    @Comment("Nom du service annoncé sur le bus.")
    public String service = "plugin";
    @Comment("Intervalle en secondes entre deux annonces de présence.")
    public int intervalSeconds = 5;
    @Comment("Durée de validité d'un service sans nouvelle annonce.")
    public int ttlSeconds = 15;
    @Comment("Si true, ignore les services d'un autre cluster.")
    public boolean sameClusterOnly = true;

    public ServiceDiscoveryConfig() {
    }

    public ServiceDiscoveryConfig(boolean enabled, String service) {
        this.enabled = enabled;
        this.service = service;
    }

    public long ttlMillis() {
        return Math.max(1000L, ttlSeconds * 1000L);
    }

    public int intervalSeconds() {
        return Math.max(1, intervalSeconds);
    }

    public ServiceDiscoveryTicker createTicker(PluginScheduler scheduler,
                                               MessageBus bus,
                                               Supplier<String> serverIdSupplier,
                                               Supplier<String> clusterSupplier) {

        return new ServiceDiscoveryTicker(scheduler, bus, this, serverIdSupplier, clusterSupplier);
    }
}
