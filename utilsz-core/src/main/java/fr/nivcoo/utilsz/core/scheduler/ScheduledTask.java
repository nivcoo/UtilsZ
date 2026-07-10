package fr.nivcoo.utilsz.core.scheduler;

public interface ScheduledTask {

    ScheduledTask NOOP = () -> {
    };

    void cancel();
}
