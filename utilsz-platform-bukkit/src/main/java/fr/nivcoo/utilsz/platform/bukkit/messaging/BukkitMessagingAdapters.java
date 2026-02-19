package fr.nivcoo.utilsz.platform.bukkit.messaging;

import fr.nivcoo.utilsz.core.messaging.BusAdapterRegistry;
import fr.nivcoo.utilsz.platform.bukkit.messaging.adapter.BukkitLocationAdapter;
import org.bukkit.Location;

public final class BukkitMessagingAdapters {

    private BukkitMessagingAdapters() {}

    public static void register() {
        BusAdapterRegistry.registerRaw(Location.class, new BukkitLocationAdapter());
    }
}
