package fr.nivcoo.utilsz.core.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public final class Platform {

    private static volatile PlatformBootstrap selected;

    private Platform() {}

    public static PlatformBootstrap bootstrap() {
        PlatformBootstrap s = selected;
        if (s != null) return s;

        synchronized (Platform.class) {
            if (selected != null) return selected;

            List<PlatformBootstrap> all = new ArrayList<>();
            for (PlatformBootstrap b : ServiceLoader.load(PlatformBootstrap.class)) {
                all.add(b);
            }

            PlatformBootstrap best = all.stream()
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
