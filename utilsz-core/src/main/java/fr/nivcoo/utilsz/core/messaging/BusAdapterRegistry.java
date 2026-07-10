package fr.nivcoo.utilsz.core.messaging;

import fr.nivcoo.utilsz.core.messaging.adapter.*;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.BooleanAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.ByteAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.CharAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.DoubleAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.FloatAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.IntegerAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.LongAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.ShortAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.primitive.StringAdapter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BusAdapterRegistry {

    private static final Map<Class<?>, BusTypeAdapter<?>> adapters = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    private BusAdapterRegistry() {
    }

    public static <T> void register(Class<T> clazz, BusTypeAdapter<T> adapter) {
        adapters.put(clazz, adapter);
    }

    @SuppressWarnings("unchecked")
    public static <T> BusTypeAdapter<T> getAdapter(Class<?> clazz) {
        if (!initialized) registerBuiltins();
        BusTypeAdapter<?> adapter = adapters.get(clazz);

        if (adapter == null && clazz.isEnum()) {
            Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) clazz;
            adapter = new EnumAnyAdapter(enumClass);
            adapters.put(clazz, adapter);
        }

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
        return (BusTypeAdapter<T>) adapter;
    }

    public static synchronized void registerBuiltins() {
        if (initialized) return;
        initialized = true;

        registerPrimitives();

        register(UUID.class, new UUIDAdapter());
        register(List.class, new ListAdapter());
        register(Map.class, new MapAdapter());
    }

    @SuppressWarnings({"rawtypes"})
    public static void registerRaw(Class<?> clazz, BusTypeAdapter adapter) {
        adapters.put(clazz, adapter);
    }

    public static void registerPrimitives() {
        register(String.class, new StringAdapter());
        registerPrimitive(Integer.class, int.class, new IntegerAdapter());
        registerPrimitive(Double.class, double.class, new DoubleAdapter());
        registerPrimitive(Float.class, float.class, new FloatAdapter());
        registerPrimitive(Boolean.class, boolean.class, new BooleanAdapter());
        registerPrimitive(Long.class, long.class, new LongAdapter());
        registerPrimitive(Short.class, short.class, new ShortAdapter());
        registerPrimitive(Byte.class, byte.class, new ByteAdapter());
        registerPrimitive(Character.class, char.class, new CharAdapter());
    }

    @SuppressWarnings("unchecked")
    public static <T> BusTypeAdapter<T> ensureAdapter(Class<T> clazz) {
        if (!initialized) registerBuiltins();
        BusTypeAdapter<T> a = (BusTypeAdapter<T>) adapters.get(clazz);
        if (a != null) return a;
        a = new GenericReflectiveAdapter<>(clazz);
        register(clazz, a);
        return a;
    }

    private static <T> void registerPrimitive(Class<T> wrapper, Class<?> primitive, BusTypeAdapter<T> adapter) {
        register(wrapper, adapter);
        registerRaw(primitive, adapter);
    }
}
