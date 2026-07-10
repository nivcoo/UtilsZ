package fr.nivcoo.utilsz.platform.bukkit.scheduler;

import fr.nivcoo.utilsz.core.scheduler.PluginScheduler;
import fr.nivcoo.utilsz.core.scheduler.ScheduledTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitPluginScheduler implements PluginScheduler {

    private final JavaPlugin plugin;

    public BukkitPluginScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public ScheduledTask run(Runnable task) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(plugin.getServer().getScheduler().runTask(plugin, task).getTaskId());
    }

    @Override
    public ScheduledTask runAsync(Runnable task) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task).getTaskId());
    }

    @Override
    public ScheduledTask runLater(Runnable task, long delayTicks) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(0L, delayTicks)).getTaskId());
    }

    @Override
    public ScheduledTask runLaterAsync(Runnable task, long delayTicks) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, Math.max(0L, delayTicks)).getTaskId());
    }

    @Override
    public ScheduledTask runRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(plugin.getServer().getScheduler().runTaskTimer(plugin, task, Math.max(0L, delayTicks), Math.max(1L, periodTicks)).getTaskId());
    }

    @Override
    public ScheduledTask runRepeatingAsync(Runnable task, long delayTicks, long periodTicks) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, Math.max(0L, delayTicks), Math.max(1L, periodTicks)).getTaskId());
    }

    private ScheduledTask wrap(int taskId) {
        return () -> plugin.getServer().getScheduler().cancelTask(taskId);
    }
}
