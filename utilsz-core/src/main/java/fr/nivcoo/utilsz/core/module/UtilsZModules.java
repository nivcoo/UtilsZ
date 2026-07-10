package fr.nivcoo.utilsz.core.module;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public final class UtilsZModules {

    private static volatile List<UtilsZModule> cached;
    private static volatile boolean loaded;

    private UtilsZModules() {}

    public static List<UtilsZModule> all() {
        List<UtilsZModule> modules = cached;
        if (modules != null) return modules;

        synchronized (UtilsZModules.class) {
            if (cached != null) return cached;

            cached = ServiceLoader.load(UtilsZModule.class, UtilsZModule.class.getClassLoader())
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .sorted(Comparator.comparingInt(UtilsZModule::priority))
                    .toList();
            return cached;
        }
    }

    public static List<UtilsZModule> compatible() {
        return all().stream()
                .filter(UtilsZModule::isCompatible)
                .toList();
    }

    public static void load() {
        if (loaded) return;

        synchronized (UtilsZModules.class) {
            if (loaded) return;

            compatible().forEach(UtilsZModule::onLoad);
            loaded = true;
        }
    }

}
