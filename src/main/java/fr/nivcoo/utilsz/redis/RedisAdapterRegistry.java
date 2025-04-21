package fr.nivcoo.utilsz.redis;

import fr.nivcoo.utilsz.redis.adapter.*;
import fr.nivcoo.utilsz.redis.adapter.primitive.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RedisAdapterRegistry {
    private static final Map<Class<?>, RedisTypeAdapter<?>> adapters = new HashMap<>();
    private static boolean initialized = false;

    public static <T> void register(Class<T> clazz, RedisTypeAdapter<T> adapter) {
        adapters.put(clazz, adapter);
    }

    @SuppressWarnings("unchecked")
    public static <T> RedisTypeAdapter<T> getAdapter(Class<?> clazz) {
        return (RedisTypeAdapter<T>) adapters.get(clazz);
    }

    public static void registerBuiltins() {
        if (initialized) return;
        initialized = true;

        register(String.class, new StringAdapter());
        register(Integer.class, new IntegerAdapter());
        register(Double.class, new DoubleAdapter());
        register(Float.class, new FloatAdapter());
        register(Boolean.class, new BooleanAdapter());
        register(Long.class, new LongAdapter());
        register(UUID.class, new UUIDAdapter());

        register(org.bukkit.Location.class, new LocationAdapter());
    }
}

