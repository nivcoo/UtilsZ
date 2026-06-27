package fr.nivcoo.utilsz.platform.velocity.hook;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import fr.nivcoo.utilsz.core.hook.HookContext;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class VelocityHookContext implements HookContext {

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;
    private final List<ScheduledTask> tasks = new ArrayList<>();

    public VelocityHookContext(ProxyServer proxy, Object plugin, Logger logger) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
    }

    public ProxyServer proxy() {
        return proxy;
    }

    public Object plugin() {
        return plugin;
    }

    public void registerListener(Object listener) {
        proxy.getEventManager().register(plugin, listener);
    }

    public void runRepeating(Runnable runnable, Duration delay, Duration period) {
        ScheduledTask task = proxy.getScheduler()
                .buildTask(plugin, runnable)
                .delay(delay)
                .repeat(period)
                .schedule();
        tasks.add(task);
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
