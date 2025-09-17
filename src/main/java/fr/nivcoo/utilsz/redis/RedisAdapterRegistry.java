package fr.nivcoo.utilsz.redis;

import fr.nivcoo.utilsz.redis.adapter.*;
import fr.nivcoo.utilsz.redis.adapter.primitive.*;
import java.util.*;

public class RedisAdapterRegistry {
    private static final Map<Class<?>, RedisTypeAdapter<?>> adapters = new HashMap<>();
    private static boolean initialized = false;

    public static <T> void register(Class<T> clazz, RedisTypeAdapter<T> adapter) {
        adapters.put(clazz, adapter);
    }

    @SuppressWarnings("unchecked")
    public static <T> RedisTypeAdapter<T> getAdapter(Class<?> clazz) {
        if (!initialized) registerBuiltins();
        RedisTypeAdapter<?> adapter = adapters.get(clazz);
        if (adapter == null) {
            for (Class<?> iface : clazz.getInterfaces()) {
                adapter = adapters.get(iface);
                if (adapter != null) break;
            }
        }
        if (adapter == null) {
            Class<?> superClass = clazz.getSuperclass();
            while (superClass != null && adapter == null) {
                adapter = adapters.get(superClass);
                superClass = superClass.getSuperclass();
            }
        }
        return (RedisTypeAdapter<T>) adapter;
    }

    public static void registerBuiltins() {
        if (initialized) return;
        initialized = true;
        registerPrimitives();
        register(org.bukkit.Location.class, new LocationAdapter());
        register(java.util.UUID.class, new UUIDAdapter());
        register(java.util.List.class,  new ListAdapter());
        register(java.util.Map.class, new MapAdapter());
    }

    public static void registerPrimitives() {
        RedisTypeAdapter<String> s = new StringAdapter();
        register(String.class, s);

        RedisTypeAdapter<Integer> i = new IntegerAdapter();
        register(Integer.class, i); register(int.class, i);

        RedisTypeAdapter<Double> d = new DoubleAdapter();
        register(Double.class, d); register(double.class, d);

        RedisTypeAdapter<Float> f = new FloatAdapter();
        register(Float.class, f); register(float.class, f);

        RedisTypeAdapter<Boolean> b = new BooleanAdapter();
        register(Boolean.class, b); register(boolean.class, b);

        RedisTypeAdapter<Long> l = new LongAdapter();
        register(Long.class, l); register(long.class, l);

        RedisTypeAdapter<Short> sh = new ShortAdapter();
        register(Short.class, sh); register(short.class, sh);

        RedisTypeAdapter<Byte> by = new ByteAdapter();
        register(Byte.class, by); register(byte.class, by);

        RedisTypeAdapter<Character> ch = new CharAdapter();
        register(Character.class, ch); register(char.class, ch);
    }

    @SuppressWarnings("unchecked")
    public static <T> RedisTypeAdapter<T> ensureAdapter(Class<T> clazz) {
        RedisTypeAdapter<T> a = (RedisTypeAdapter<T>) adapters.get(clazz);
        if (a != null) return a;
        a = new fr.nivcoo.utilsz.redis.adapter.GenericReflectiveAdapter<>(clazz);
        register(clazz, a);
        return a;
    }
}
