package fr.nivcoo.utilsz.core.config;

import fr.nivcoo.utilsz.core.config.annotations.Converter;
import fr.nivcoo.utilsz.core.config.spi.ConverterProvider;

import java.util.Comparator;
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

            ServiceLoader.load(ConverterProvider.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .sorted(Comparator.comparingInt(ConverterProvider::priority))
                    .forEach(p -> putProvider(out, p));

            cached = Map.copyOf(out);
            return cached;
        }
    }

    private static void putProvider(Map<Class<?>, Supplier<Converter<?>>> out, ConverterProvider provider) {
        Map<Class<?>, Supplier<Converter<?>>> converters = provider.converters();
        if (converters != null) out.putAll(converters);
    }
}
