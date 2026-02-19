package fr.nivcoo.utilsz.runtime;

import fr.nivcoo.utilsz.core.platform.PlatformBootstrap;

public final class Runtime {

    private static volatile PlatformBootstrap platform;

    private Runtime() {}

    static void setPlatform(PlatformBootstrap bootstrap) {
        platform = bootstrap;
    }

    public static PlatformBootstrap platform() {
        if (platform == null) {
            throw new IllegalStateException("UtilsZ runtime not initialized");
        }
        return platform;
    }
}
