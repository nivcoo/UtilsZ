package fr.nivcoo.utilsz.core.database;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import fr.nivcoo.utilsz.core.conversion.Converter;
import fr.nivcoo.utilsz.core.conversion.ConverterRegistry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.UUID;

public final class DatabaseCodecs {

    private static final Gson GSON = new Gson();
    private static final Map<Class<?>, DatabaseCodec<?>> CODECS = new LinkedHashMap<>();

    static {
        register(new SimpleCodec<>(String.class, value -> value == null ? null : String.valueOf(value), value -> value));
        register(new SimpleCodec<>(Integer.class, DatabaseCodecs::toInt, value -> value));
        register(new SimpleCodec<>(Long.class, DatabaseCodecs::toLong, value -> value));
        register(new SimpleCodec<>(Boolean.class, DatabaseCodecs::toBoolean, value -> value ? 1 : 0));
        register(new SimpleCodec<>(BigDecimal.class, DatabaseCodecs::toBigDecimal, BigDecimal::toPlainString));
        register(new SimpleCodec<>(UUID.class, DatabaseCodecs::toUuid, UUID::toString));
    }

    private DatabaseCodecs() {
    }

    public static void register(DatabaseCodec<?> codec) {
        if (codec == null || codec.type() == null) return;
        CODECS.put(codec.type(), codec);
    }

    public static <T> T decode(Object value, Class<T> type) {
        if (type == null || value == null) return null;

        Class<T> resolvedType = boxed(type);
        if (resolvedType.isInstance(value)) return resolvedType.cast(value);

        DatabaseCodec<T> codec = codec(resolvedType);
        if (codec != null) return codec.fromDatabase(value);
        if (resolvedType.isEnum()) return decodeEnum(value, resolvedType);

        throw new IllegalArgumentException("No database codec registered for " + resolvedType.getName());
    }

    @SuppressWarnings("unchecked")
    public static Object encode(Object value) {
        if (value == null) return null;
        if (value instanceof Collection<?> collection) return encodeList(collection);
        DatabaseCodec<Object> codec = codec((Class<Object>) value.getClass());
        if (codec != null) return codec.toDatabase(value);
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        return value;
    }

    public static Object encode(Object value, Class<?> targetType) {
        if (targetType == null || value == null) return encode(value);

        Class<?> boxedType = boxed(targetType);
        if (boxedType.isInstance(value)) return encode(value);

        Object converted = decode(value, boxedType);
        return encode(converted);
    }

    public static Object encodeList(Collection<?> values) {
        if (values == null) return null;

        JsonArray array = new JsonArray();
        for (Object value : values) {
            array.add(toJsonElement(encode(value)));
        }
        return GSON.toJson(array);
    }

    public static <T> List<T> decodeList(Object value, Class<T> elementType) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) {
            List<T> out = new ArrayList<>(collection.size());
            for (Object element : collection) {
                out.add(decode(element, elementType));
            }
            return out;
        }

        String text = String.valueOf(value);
        if (text.isBlank()) return List.of();

        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonArray()) {
            return List.of(decode(fromJsonElement(parsed), elementType));
        }

        JsonArray array = parsed.getAsJsonArray();
        List<T> out = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            out.add(decode(fromJsonElement(element), elementType));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T> DatabaseCodec<T> codec(Class<T> type) {
        DatabaseCodec<?> exact = CODECS.get(boxed(type));
        if (exact != null) return (DatabaseCodec<T>) exact;

        Map<Class<?>, Supplier<DatabaseCodec<?>>> moduleCodecs = DatabaseCodecRegistry.all();
        Supplier<DatabaseCodec<?>> exactSupplier = moduleCodecs.get(boxed(type));
        if (exactSupplier != null) return (DatabaseCodec<T>) exactSupplier.get();

        Map<Class<?>, Supplier<Converter<?>>> converters = ConverterRegistry.all();
        Supplier<Converter<?>> exactConverter = converters.get(boxed(type));
        if (exactConverter != null) return converterCodec(type, exactConverter);

        for (Map.Entry<Class<?>, DatabaseCodec<?>> entry : CODECS.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (DatabaseCodec<T>) entry.getValue();
            }
        }

        for (Map.Entry<Class<?>, Supplier<DatabaseCodec<?>>> entry : moduleCodecs.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (DatabaseCodec<T>) entry.getValue().get();
            }
        }

        for (Map.Entry<Class<?>, Supplier<Converter<?>>> entry : converters.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return converterCodec(type, entry.getValue());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> DatabaseCodec<T> converterCodec(Class<T> type, Supplier<Converter<?>> supplier) {
        return new ConverterDatabaseCodec<>(type, () -> (Converter<T>) supplier.get());
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> boxed(Class<T> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return (Class<T>) Integer.class;
        if (type == long.class) return (Class<T>) Long.class;
        if (type == boolean.class) return (Class<T>) Boolean.class;
        if (type == double.class) return (Class<T>) Double.class;
        if (type == float.class) return (Class<T>) Float.class;
        if (type == short.class) return (Class<T>) Short.class;
        if (type == byte.class) return (Class<T>) Byte.class;
        if (type == char.class) return (Class<T>) Character.class;
        return type;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(String.valueOf(value));
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        String text = String.valueOf(value);
        return text.isBlank() ? null : UUID.fromString(text);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T decodeEnum(Object value, Class<T> type) {
        String text = String.valueOf(value);
        return text.isBlank() ? null : (T) Enum.valueOf((Class<? extends Enum>) type, text);
    }

    private static JsonElement toJsonElement(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Character character) return new JsonPrimitive(character);
        return new JsonPrimitive(String.valueOf(value));
    }

    private static Object fromJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonPrimitive()) return GSON.toJson(element);

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsNumber();
        return primitive.getAsString();
    }

    private record SimpleCodec<T>(Class<T> type, Decoder<T> decoder, Encoder<T> encoder) implements DatabaseCodec<T> {

        @Override
        public Object toDatabase(T value) {
            return value == null ? null : encoder.encode(value);
        }

        @Override
        public T fromDatabase(Object value) {
            return value == null ? null : decoder.decode(value);
        }
    }

    private interface Decoder<T> {
        T decode(Object value);
    }

    private interface Encoder<T> {
        Object encode(T value);
    }
}
