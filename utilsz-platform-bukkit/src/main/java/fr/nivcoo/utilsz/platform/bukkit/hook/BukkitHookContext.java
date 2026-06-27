package fr.nivcoo.utilsz.platform.bukkit.hook;

import fr.nivcoo.utilsz.core.hook.HookContext;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class BukkitHookContext implements HookContext {

    private final JavaPlugin plugin;
    private final List<Integer> taskIds = new ArrayList<>();

    public BukkitHookContext(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    public void runRepeating(Runnable runnable, long delay, long period) {
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period).getTaskId();
        taskIds.add(taskId);
    }

    public void cancelTasks() {
        for (int taskId : taskIds) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        taskIds.clear();
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
