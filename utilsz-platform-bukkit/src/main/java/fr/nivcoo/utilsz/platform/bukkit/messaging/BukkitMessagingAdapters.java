package fr.nivcoo.utilsz.platform.bukkit.messaging;

import fr.nivcoo.utilsz.core.messaging.BusAdapterRegistry;
import fr.nivcoo.utilsz.platform.bukkit.messaging.adapter.BukkitLocationAdapter;
import fr.nivcoo.utilsz.platform.bukkit.messaging.adapter.BukkitItemStackAdapter;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public final class BukkitMessagingAdapters {

    private BukkitMessagingAdapters() {}

    public static void register() {
        BusAdapterRegistry.registerRaw(Location.class, new BukkitLocationAdapter());
        BusAdapterRegistry.register(ItemStack.class, new BukkitItemStackAdapter());
    }
}
