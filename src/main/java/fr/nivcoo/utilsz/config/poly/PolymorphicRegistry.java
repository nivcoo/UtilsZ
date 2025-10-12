package fr.nivcoo.utilsz.config.poly;

import java.util.HashMap;
import java.util.Map;

public final class PolymorphicRegistry {

    private static final Map<Class<?>, Map<String, Class<?>>> REG = new HashMap<>();

    public static synchronized <B> void register(Class<B> base, String type, Class<? extends B> impl) {
        REG.computeIfAbsent(base, k -> new HashMap<>()).put(type.toLowerCase(), impl);
    }

    @SuppressWarnings("unchecked")
    public static <B> Class<? extends B> resolve(Class<B> base, String type) {
        var byBase = REG.getOrDefault(base, Map.of());
        Class<?> c = byBase.get(type.toLowerCase());
        if (c == null) throw new IllegalArgumentException("Unknown type '" + type + "' for " + base.getSimpleName());
        return (Class<? extends B>) c;
    }
}
