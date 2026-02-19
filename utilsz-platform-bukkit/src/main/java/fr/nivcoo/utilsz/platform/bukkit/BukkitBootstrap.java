package fr.nivcoo.utilsz.platform.bukkit;

import fr.nivcoo.utilsz.core.platform.MainThreadExecutor;
import fr.nivcoo.utilsz.core.platform.PlatformBootstrap;
import fr.nivcoo.utilsz.platform.bukkit.messaging.BukkitMessagingAdapters;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class BukkitBootstrap implements PlatformBootstrap {

    private final MainThreadExecutor executor;

    public BukkitBootstrap() {
        Plugin plugin = Bukkit.getPluginManager().getPlugins().length > 0
                ? Bukkit.getPluginManager().getPlugins()[0]
                : null;

        this.executor = task -> {
            if (plugin != null) Bukkit.getScheduler().runTask(plugin, task);
            else task.run();
        };
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
    public MainThreadExecutor mainThreadExecutor() {
        return executor;
    }

    @Override
    public void onLoad() {
        BukkitMessagingAdapters.register();
    }
}
