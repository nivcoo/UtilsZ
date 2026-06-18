package fr.nivcoo.utilsz.core.config;

import fr.nivcoo.utilsz.core.config.annotations.Converter;
import fr.nivcoo.utilsz.core.module.UtilsZModule;
import fr.nivcoo.utilsz.core.module.UtilsZModules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ConverterRegistry {

    private static volatile Map<Class<?>, Supplier<Converter<?>>> cached;

    private ConverterRegistry() {}

    public static Map<Class<?>, Supplier<Converter<?>>> all() {
        Map<Class<?>, Supplier<Converter<?>>> c = cached;
        if (c != null) return c;

        synchronized (ConverterRegistry.class) {
            if (cached != null) return cached;

            Map<Class<?>, Supplier<Converter<?>>> out = new LinkedHashMap<>();

            UtilsZModules.compatible()
                    .forEach(module -> putConverters(out, module));

            cached = Map.copyOf(out);
            return cached;
        }
    }

    private static void putConverters(Map<Class<?>, Supplier<Converter<?>>> out, UtilsZModule module) {
        Map<Class<?>, Supplier<Converter<?>>> converters = module.converters();
        if (converters != null) out.putAll(converters);
    }
}
