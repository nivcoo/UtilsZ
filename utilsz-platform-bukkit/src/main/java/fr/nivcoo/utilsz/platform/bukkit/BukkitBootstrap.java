package fr.nivcoo.utilsz.platform.bukkit;

import fr.nivcoo.utilsz.core.platform.MainThreadExecutor;
import fr.nivcoo.utilsz.core.platform.PlatformBootstrap;
import fr.nivcoo.utilsz.platform.bukkit.messaging.BukkitMessagingAdapters;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class BukkitBootstrap implements PlatformBootstrap {

    public BukkitBootstrap() {
    }

    @Override
    public boolean isCompatible() {
        try {
            Class.forName("org.bukkit.Bukkit", false, BukkitBootstrap.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public void onLoad() {
        BukkitMessagingAdapters.register();
    }
}
