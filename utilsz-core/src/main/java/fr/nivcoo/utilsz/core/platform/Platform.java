package fr.nivcoo.utilsz.core.platform;

import java.util.Comparator;
import java.util.ServiceLoader;

public final class Platform {

    private static volatile PlatformBootstrap selected;

    private Platform() {}

    public static PlatformBootstrap bootstrap() {
        PlatformBootstrap s = selected;
        if (s != null) return s;

        synchronized (Platform.class) {
            if (selected != null) return selected;

            PlatformBootstrap best = ServiceLoader.load(PlatformBootstrap.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(PlatformBootstrap::isCompatible)
                    .max(Comparator.comparingInt(PlatformBootstrap::priority))
                    .orElse(null);

            if (best == null) {
                throw new IllegalStateException("No compatible PlatformBootstrap found on classpath.");
            }

            best.onLoad();
            selected = best;
            return best;
        }
    }
}
