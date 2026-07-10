package fr.nivcoo.utilsz.platform.velocity.hook;

import com.velocitypowered.api.proxy.ProxyServer;
import fr.nivcoo.utilsz.core.hook.HookContext;
import fr.nivcoo.utilsz.core.scheduler.ScheduledTask;
import fr.nivcoo.utilsz.platform.velocity.scheduler.VelocityPluginScheduler;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class VelocityHookContext implements HookContext {

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;
    private final VelocityPluginScheduler scheduler;
    private final List<ScheduledTask> tasks = new ArrayList<>();

    public VelocityHookContext(ProxyServer proxy, Object plugin, Logger logger) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
        this.scheduler = new VelocityPluginScheduler(proxy, plugin);
    }

    public ProxyServer proxy() {
        return proxy;
    }

    public Object plugin() {
        return plugin;
    }

    public VelocityPluginScheduler scheduler() {
        return scheduler;
    }

    public void registerListener(Object listener) {
        proxy.getEventManager().register(plugin, listener);
    }

    public void runRepeating(Runnable runnable, Duration delay, Duration period) {
        long delayTicks = Math.max(0L, delay.toMillis() / 50L);
        long periodTicks = Math.max(1L, period.toMillis() / 50L);
        tasks.add(scheduler.runRepeatingAsync(runnable, delayTicks, periodTicks));
    }

    public void cancelTasks() {
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    @Override
    public void logHookInfo(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }

    @Override
    public void logHookWarning(String message) {
        if (logger != null) {
            logger.warn(message);
        }
    }
}
