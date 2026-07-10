package fr.nivcoo.utilsz.core.scheduler;

public interface PluginScheduler {

    ScheduledTask run(Runnable task);

    ScheduledTask runAsync(Runnable task);

    ScheduledTask runLater(Runnable task, long delayTicks);

    ScheduledTask runLaterAsync(Runnable task, long delayTicks);

    ScheduledTask runRepeating(Runnable task, long delayTicks, long periodTicks);

    ScheduledTask runRepeatingAsync(Runnable task, long delayTicks, long periodTicks);
}
