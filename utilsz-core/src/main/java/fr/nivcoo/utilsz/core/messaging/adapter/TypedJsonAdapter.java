package fr.nivcoo.utilsz.core.messaging.adapter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.nivcoo.utilsz.core.messaging.BusAdapterRegistry;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

final class TypedJsonAdapter {

    private static final Gson GSON = new Gson();

    private TypedJsonAdapter() {
    }

    static JsonElement serialize(Object value, Type declaredType) {
        if (value == null) return JsonNull.INSTANCE;

        Type type = normalize(declaredType);
        Class<?> raw = rawClass(type);

        if (type instanceof ParameterizedType parameterizedType) {
            if (Collection.class.isAssignableFrom(raw)) {
                return serializeCollection((Collection<?>) value, elementType(parameterizedType));
            }
            if (Map.class.isAssignableFrom(raw)) {
                return serializeMap((Map<?, ?>) value, valueType(parameterizedType));
            }
        }

        BusTypeAdapter<Object> adapter = adapterFor(value.getClass());
        if (adapter != null) return adapter.serialize(value);

        return GSON.toJsonTree(value);
    }

    static Object deserialize(JsonElement element, Type declaredType) {
        if (element == null || element.isJsonNull()) return null;

        Type type = normalize(declaredType);
        Class<?> raw = rawClass(type);

        if (type instanceof ParameterizedType parameterizedType) {
            if (Collection.class.isAssignableFrom(raw)) {
                return deserializeCollection(element, raw, elementType(parameterizedType));
            }
            if (Map.class.isAssignableFrom(raw)) {
                return deserializeMap(element, raw, keyType(parameterizedType), valueType(parameterizedType));
            }
        }

        return deserializeClass(element, raw);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BusTypeAdapter<Object> adapterFor(Class<?> clazz) {
        BusTypeAdapter<Object> adapter = BusAdapterRegistry.getAdapter(clazz);
        if (adapter != null) return adapter;

        if (!canReflect(clazz)) return null;
        try {
            return (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) clazz);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JsonObject serializeCollection(Collection<?> value, Type elementType) {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();

        for (Object element : value) {
            array.add(serialize(element, elementType));
        }

        json.add("value", array);
        return json;
    }

    private static JsonObject serializeMap(Map<?, ?> value, Type valueType) {
        JsonObject json = new JsonObject();
        JsonObject data = new JsonObject();

        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (entry.getKey() == null) continue;
            data.add(mapKey(entry.getKey()), serialize(entry.getValue(), valueType));
        }

        json.add("value", data);
        return json;
    }

    private static Collection<Object> deserializeCollection(JsonElement element, Class<?> raw, Type elementType) {
        Collection<Object> result = newCollection(raw);
        JsonArray array = collectionArray(element);
        if (array == null) return result;

        for (JsonElement entry : array) {
            result.add(deserialize(entry, elementType));
        }

        return result;
    }

    private static Map<Object, Object> deserializeMap(JsonElement element, Class<?> raw, Type keyType, Type valueType) {
        Map<Object, Object> result = newMap(raw);
        JsonObject data = mapObject(element);
        if (data == null) return result;

        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            result.put(deserializeKey(entry.getKey(), keyType), deserialize(entry.getValue(), valueType));
        }

        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object deserializeClass(JsonElement element, Class<?> raw) {
        if (raw == Object.class) return GSON.fromJson(element, Object.class);
        if (raw == String.class) return element.isJsonObject()
                ? adapterFor(raw).deserialize(element.getAsJsonObject())
                : element.getAsString();
        if (raw == UUID.class && element.isJsonPrimitive()) return UUID.fromString(element.getAsString());
        if (raw.isEnum() && element.isJsonPrimitive()) return Enum.valueOf((Class) raw, element.getAsString());

        BusTypeAdapter<Object> adapter = adapterFor(raw);
        if (adapter != null && element.isJsonObject()) return adapter.deserialize(element.getAsJsonObject());

        return GSON.fromJson(element, raw);
    }

    private static Type normalize(Type type) {
        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0) return normalize(upperBounds[0]);
            return Object.class;
        }
        return type == null ? Object.class : type;
    }

    private static Type elementType(ParameterizedType type) {
        Type[] args = type.getActualTypeArguments();
        return args.length == 0 ? Object.class : normalize(args[0]);
    }

    private static Type keyType(ParameterizedType type) {
        Type[] args = type.getActualTypeArguments();
        return args.length == 0 ? String.class : normalize(args[0]);
    }

    private static Type valueType(ParameterizedType type) {
        Type[] args = type.getActualTypeArguments();
        return args.length < 2 ? Object.class : normalize(args[1]);
    }

    private static Class<?> rawClass(Type type) {
        Type normalized = normalize(type);
        if (normalized instanceof Class<?> clazz) return clazz;
        if (normalized instanceof ParameterizedType parameterizedType) {
            Type raw = parameterizedType.getRawType();
            if (raw instanceof Class<?> clazz) return clazz;
        }
        return Object.class;
    }

    private static JsonArray collectionArray(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonArray()) return element.getAsJsonArray();
        if (!element.isJsonObject()) return null;

        JsonObject object = element.getAsJsonObject();
        JsonElement value = object.get("value");
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static JsonObject mapObject(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonObject()) return null;

        JsonObject object = element.getAsJsonObject();
        JsonElement value = object.get("value");
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : object;
    }

    private static Collection<Object> newCollection(Class<?> raw) {
        Object created = instantiate(raw);
        if (created instanceof Collection<?> collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> result = (Collection<Object>) collection;
            return result;
        }

        if (Set.class.isAssignableFrom(raw)) return new LinkedHashSet<>();
        if (Queue.class.isAssignableFrom(raw)) return new ArrayDeque<>();
        return new ArrayList<>();
    }

    private static Map<Object, Object> newMap(Class<?> raw) {
        Object created = instantiate(raw);
        if (created instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> result = (Map<Object, Object>) map;
            return result;
        }

        return new LinkedHashMap<>();
    }

    private static Object instantiate(Class<?> raw) {
        if (raw == null || raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) return null;
        try {
            Constructor<?> constructor = raw.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object deserializeKey(String key, Type keyType) {
        Class<?> raw = rawClass(keyType);
        if (raw == String.class || raw == Object.class) return key;
        if (raw == UUID.class) return UUID.fromString(key);
        if (raw == Integer.class || raw == int.class) return Integer.parseInt(key);
        if (raw == Long.class || raw == long.class) return Long.parseLong(key);
        if (raw == Short.class || raw == short.class) return Short.parseShort(key);
        if (raw == Byte.class || raw == byte.class) return Byte.parseByte(key);
        if (raw == Double.class || raw == double.class) return Double.parseDouble(key);
        if (raw == Float.class || raw == float.class) return Float.parseFloat(key);
        if (raw == Boolean.class || raw == boolean.class) return Boolean.parseBoolean(key);
        if (raw.isEnum()) return Enum.valueOf((Class) raw, key);
        return GSON.fromJson(new JsonPrimitive(key), keyType);
    }

    private static String mapKey(Object key) {
        if (key instanceof Enum<?> enumValue) return enumValue.name();
        return String.valueOf(key);
    }

    private static boolean canReflect(Class<?> clazz) {
        if (clazz == null) return false;
        if (clazz.isPrimitive() || clazz.isArray() || clazz.isEnum()) return false;

        Package pkg = clazz.getPackage();
        if (pkg == null) return true;

        String name = pkg.getName();
        return !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("jdk.")
                && !name.startsWith("sun.")
                && !name.startsWith("com.google.gson.");
    }
}
