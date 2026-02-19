package fr.nivcoo.utilsz.core.platform;

public interface PlatformBootstrap {
    boolean isCompatible();
    int priority();
    default void onLoad() {}
}
