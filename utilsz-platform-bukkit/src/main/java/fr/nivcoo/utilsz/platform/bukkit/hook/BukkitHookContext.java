package fr.nivcoo.utilsz.platform.bukkit.hook;

import fr.nivcoo.utilsz.core.hook.HookContext;
import fr.nivcoo.utilsz.core.scheduler.ScheduledTask;
import fr.nivcoo.utilsz.platform.bukkit.scheduler.BukkitPluginScheduler;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class BukkitHookContext implements HookContext {

    private final JavaPlugin plugin;
    private final BukkitPluginScheduler scheduler;
    private final List<ScheduledTask> tasks = new ArrayList<>();

    public BukkitHookContext(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = new BukkitPluginScheduler(plugin);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public BukkitPluginScheduler scheduler() {
        return scheduler;
    }

    public void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    public void runRepeating(Runnable runnable, long delay, long period) {
        tasks.add(scheduler.runRepeating(runnable, delay, period));
    }

    public void cancelTasks() {
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    @Override
    public void logHookInfo(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void logHookWarning(String message) {
        plugin.getLogger().warning(message);
    }
}
