package fr.nivcoo.utilsz.core.database;

import fr.nivcoo.utilsz.core.module.UtilsZModule;
import fr.nivcoo.utilsz.core.module.UtilsZModules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class DatabaseCodecRegistry {

    private static volatile Map<Class<?>, Supplier<DatabaseCodec<?>>> cached;

    private DatabaseCodecRegistry() {
    }

    public static Map<Class<?>, Supplier<DatabaseCodec<?>>> all() {
        Map<Class<?>, Supplier<DatabaseCodec<?>>> current = cached;
        if (current != null) return current;

        synchronized (DatabaseCodecRegistry.class) {
            if (cached != null) return cached;

            Map<Class<?>, Supplier<DatabaseCodec<?>>> out = new LinkedHashMap<>();
            UtilsZModules.compatible().forEach(module -> putCodecs(out, module));

            cached = Map.copyOf(out);
            return cached;
        }
    }

    private static void putCodecs(Map<Class<?>, Supplier<DatabaseCodec<?>>> out, UtilsZModule module) {
        Map<Class<?>, Supplier<DatabaseCodec<?>>> codecs = module.databaseCodecs();
        if (codecs != null) out.putAll(codecs);
    }
}
