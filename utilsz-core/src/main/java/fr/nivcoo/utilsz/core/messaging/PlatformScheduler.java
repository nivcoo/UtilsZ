package fr.nivcoo.utilsz.core.messaging;

public interface PlatformScheduler {
    void runOnMainThread(Runnable r);
}
