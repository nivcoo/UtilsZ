package fr.nivcoo.utilsz.core.config;

import fr.nivcoo.utilsz.core.config.annotations.Converter;
import fr.nivcoo.utilsz.core.config.spi.ConverterProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
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

            for (ConverterProvider p : ServiceLoader.load(ConverterProvider.class)) {
                Map<Class<?>, Supplier<Converter<?>>> m = p.converters();
                if (m != null) out.putAll(m);
            }

            cached = Map.copyOf(out);
            return cached;
        }
    }
}
