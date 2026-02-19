package fr.nivcoo.utilsz.platform.velocity;

import fr.nivcoo.utilsz.core.platform.MainThreadExecutor;
import fr.nivcoo.utilsz.core.platform.PlatformBootstrap;

public final class VelocityBootstrap implements PlatformBootstrap {

    public VelocityBootstrap() {
    }

    @Override
    public boolean isCompatible() {
        try {
            Class.forName(
                    "com.velocitypowered.api.proxy.ProxyServer",
                    false,
                    VelocityBootstrap.class.getClassLoader()
            );
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int priority() {
        return 100;
    }

}
