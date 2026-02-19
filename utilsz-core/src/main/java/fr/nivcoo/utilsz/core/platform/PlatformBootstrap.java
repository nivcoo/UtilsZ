package fr.nivcoo.utilsz.core.platform;

public interface PlatformBootstrap {
    boolean isCompatible();
    int priority();
    MainThreadExecutor mainThreadExecutor();
    default void onLoad() {}
}
