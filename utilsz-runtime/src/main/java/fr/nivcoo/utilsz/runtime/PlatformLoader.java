package fr.nivcoo.utilsz.runtime;

import fr.nivcoo.utilsz.core.platform.PlatformBootstrap;

import java.util.Comparator;
import java.util.ServiceLoader;

final class PlatformLoader {

    private PlatformLoader() {}

    static PlatformBootstrap detect() {

        PlatformBootstrap selected = ServiceLoader
                .load(PlatformBootstrap.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(PlatformBootstrap::isCompatible)
                .max(Comparator.comparingInt(PlatformBootstrap::priority))
                .orElseThrow(() ->
                        new IllegalStateException("No compatible PlatformBootstrap found")
                );

        selected.onLoad();
        return selected;
    }
}
