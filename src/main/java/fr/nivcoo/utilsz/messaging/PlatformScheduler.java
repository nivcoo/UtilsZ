package fr.nivcoo.utilsz.messaging;

public interface PlatformScheduler {
    void runOnMainThread(Runnable r);
}
