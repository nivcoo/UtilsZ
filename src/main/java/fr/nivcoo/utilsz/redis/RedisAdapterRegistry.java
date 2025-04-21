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
        if (!initialized) {
            registerBuiltins();
        }

        RedisTypeAdapter<?> adapter = adapters.get(clazz);

        if (adapter == null && RedisSerializable.class.isAssignableFrom(clazz)) {
            if (clazz.isAnnotationPresent(RedisAction.class)) {
                adapter = new GenericReflectiveAdapter<>((Class<T>) clazz);
                adapters.put(clazz, adapter);
            }
        }

        return (RedisTypeAdapter<T>) adapter;
    }


    public static void registerBuiltins() {
        if (initialized) return;
        initialized = true;

        registerPrimitives();

        register(org.bukkit.Location.class, new LocationAdapter());

        register(UUID.class, new UUIDAdapter());
    }

    public static void registerPrimitives() {
        register(String.class, new StringAdapter());

        RedisTypeAdapter<Integer> intAdapter = new IntegerAdapter();
        register(Integer.class, intAdapter);
        register(int.class, intAdapter);

        RedisTypeAdapter<Double> doubleAdapter = new DoubleAdapter();
        register(Double.class, doubleAdapter);
        register(double.class, doubleAdapter);

        RedisTypeAdapter<Float> floatAdapter = new FloatAdapter();
        register(Float.class, floatAdapter);
        register(float.class, floatAdapter);

        RedisTypeAdapter<Boolean> boolAdapter = new BooleanAdapter();
        register(Boolean.class, boolAdapter);
        register(boolean.class, boolAdapter);

        RedisTypeAdapter<Long> longAdapter = new LongAdapter();
        register(Long.class, longAdapter);
        register(long.class, longAdapter);

        RedisTypeAdapter<Short> shortAdapter = new ShortAdapter();
        register(Short.class, shortAdapter);
        register(short.class, shortAdapter);

        RedisTypeAdapter<Byte> byteAdapter = new ByteAdapter();
        register(Byte.class, byteAdapter);
        register(byte.class, byteAdapter);

        RedisTypeAdapter<Character> charAdapter = new CharAdapter();
        register(Character.class, charAdapter);
        register(char.class, charAdapter);

    }

}

