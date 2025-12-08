package fr.nivcoo.utilsz.messaging;

import fr.nivcoo.utilsz.messaging.adapter.*;
import fr.nivcoo.utilsz.messaging.adapter.primitive.BooleanAdapter;
import fr.nivcoo.utilsz.messaging.adapter.primitive.ByteAdapter;
import fr.nivcoo.utilsz.messaging.adapter.primitive.CharAdapter;
import fr.nivcoo.utilsz.messaging.adapter.primitive.DoubleAdapter;
import fr.nivcoo.utilsz.messaging.adapter.primitive.FloatAdapter;
import fr.nivcoo.utilsz.messaging.adapter.primitive.IntegerAdapter;
import fr.nivcoo.utilsz.messaging.adapter.primitive.LongAdapter;
import fr.nivcoo.utilsz.messaging.adapter.primitive.ShortAdapter;
import fr.nivcoo.utilsz.messaging.adapter.primitive.StringAdapter;

import java.util.HashMap;
import java.util.Map;

public final class BusAdapterRegistry {

    private static final Map<Class<?>, BusTypeAdapter<?>> adapters = new HashMap<>();
    private static boolean initialized = false;

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

    public static void registerBuiltins() {
        if (initialized) return;
        initialized = true;
        registerPrimitives();
        register(org.bukkit.Location.class, new LocationAdapter());
        register(java.util.UUID.class, new UUIDAdapter());
        register(java.util.List.class, new ListAdapter());
        register(java.util.Map.class, new MapAdapter());
    }

    public static void registerPrimitives() {
        BusTypeAdapter<String> s = new StringAdapter();
        register(String.class, s);

        BusTypeAdapter<Integer> i = new IntegerAdapter();
        register(Integer.class, i);
        register(int.class, i);

        BusTypeAdapter<Double> d = new DoubleAdapter();
        register(Double.class, d);
        register(double.class, d);

        BusTypeAdapter<Float> f = new FloatAdapter();
        register(Float.class, f);
        register(float.class, f);

        BusTypeAdapter<Boolean> b = new BooleanAdapter();
        register(Boolean.class, b);
        register(boolean.class, b);

        BusTypeAdapter<Long> l = new LongAdapter();
        register(Long.class, l);
        register(long.class, l);

        BusTypeAdapter<Short> sh = new ShortAdapter();
        register(Short.class, sh);
        register(short.class, sh);

        BusTypeAdapter<Byte> by = new ByteAdapter();
        register(Byte.class, by);
        register(byte.class, by);

        BusTypeAdapter<Character> ch = new CharAdapter();
        register(Character.class, ch);
        register(char.class, ch);
    }

    @SuppressWarnings("unchecked")
    public static <T> BusTypeAdapter<T> ensureAdapter(Class<T> clazz) {
        BusTypeAdapter<T> a = (BusTypeAdapter<T>) adapters.get(clazz);
        if (a != null) return a;
        a = new GenericReflectiveAdapter<>(clazz);
        register(clazz, a);
        return a;
    }
}
