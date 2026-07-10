package fr.nivcoo.utilsz.platform.velocity.scheduler;

import com.velocitypowered.api.proxy.ProxyServer;
import fr.nivcoo.utilsz.core.scheduler.PluginScheduler;
import fr.nivcoo.utilsz.core.scheduler.ScheduledTask;

import java.time.Duration;

public final class VelocityPluginScheduler implements PluginScheduler {

    private final ProxyServer proxy;
    private final Object plugin;

    public VelocityPluginScheduler(ProxyServer proxy, Object plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
    }

    public ProxyServer proxy() {
        return proxy;
    }

    public Object plugin() {
        return plugin;
    }

    @Override
    public ScheduledTask run(Runnable task) {
        return runAsync(task);
    }

    @Override
    public ScheduledTask runAsync(Runnable task) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(proxy.getScheduler().buildTask(plugin, task).schedule());
    }

    @Override
    public ScheduledTask runLater(Runnable task, long delayTicks) {
        return runLaterAsync(task, delayTicks);
    }

    @Override
    public ScheduledTask runLaterAsync(Runnable task, long delayTicks) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(proxy.getScheduler().buildTask(plugin, task).delay(ticks(delayTicks)).schedule());
    }

    @Override
    public ScheduledTask runRepeating(Runnable task, long delayTicks, long periodTicks) {
        return runRepeatingAsync(task, delayTicks, periodTicks);
    }

    @Override
    public ScheduledTask runRepeatingAsync(Runnable task, long delayTicks, long periodTicks) {
        if (task == null) return ScheduledTask.NOOP;
        return wrap(proxy.getScheduler()
                .buildTask(plugin, task)
                .delay(ticks(delayTicks))
                .repeat(ticks(Math.max(1L, periodTicks)))
                .schedule());
    }

    private ScheduledTask wrap(com.velocitypowered.api.scheduler.ScheduledTask task) {
        return task == null ? ScheduledTask.NOOP : task::cancel;
    }

    private static Duration ticks(long ticks) {
        return Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }
}
