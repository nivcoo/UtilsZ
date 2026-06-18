package fr.nivcoo.utilsz.platform.velocity;

import fr.nivcoo.utilsz.core.module.UtilsZModule;

public final class VelocityModule implements UtilsZModule {

    @Override
    public boolean isCompatible() {
        try {
            Class.forName(
                    "com.velocitypowered.api.proxy.ProxyServer",
                    false,
                    VelocityModule.class.getClassLoader()
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
