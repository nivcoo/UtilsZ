package fr.nivcoo.utilsz.messaging.adapter;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.messaging.BusAdapterRegistry;
import fr.nivcoo.utilsz.messaging.BusTypeAdapter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class GenericReflectiveAdapter<T> implements BusTypeAdapter<T> {

    private final Class<T> type;
    private final Field[] fields;
    private final Constructor<T> constructor;

    public GenericReflectiveAdapter(Class<T> type) {
        this.type = type;
        this.fields = type.getDeclaredFields();

        try {
            this.constructor = type.getDeclaredConstructor(getFieldTypes(fields));
            this.constructor.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Cannot find constructor for class " + type.getName(), e);
        }
    }

    @Override
    public JsonObject serialize(T value) {
        JsonObject json = new JsonObject();
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                if (fieldValue == null) {
                    json.add(field.getName(), JsonNull.INSTANCE);
                    continue;
                }
                BusTypeAdapter<Object> adapter = getAdapter(field.getType());
                if (adapter == null)
                    throw new RuntimeException("No adapter for field " + field.getName() + " in " + type.getSimpleName()
                            + " of type " + field.getType().getSimpleName());
                json.add(field.getName(), adapter.serialize(fieldValue));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error serializing " + type.getName(), e);
        }
        return json;
    }

    @Override
    public T deserialize(JsonObject json) {
        Object[] args = new Object[fields.length];
        try {
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                String name = field.getName();
                JsonElement el = json.has(name) ? json.get(name) : JsonNull.INSTANCE;

                if (el == null || el.isJsonNull()) {
                    args[i] = null;
                    continue;
                }

                BusTypeAdapter<Object> adapter = getAdapter(field.getType());
                if (adapter == null)
                    throw new RuntimeException("No adapter for field " + name + " in " + type.getSimpleName());

                args[i] = adapter.deserialize(el.getAsJsonObject());
            }
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing " + type.getName(), e);
        }
    }

    private BusTypeAdapter<Object> getAdapter(Class<?> fieldType) {
        return BusAdapterRegistry.getAdapter(fieldType);
    }

    private static Class<?>[] getFieldTypes(Field[] fields) {
        Class<?>[] types = new Class[fields.length];
        for (int i = 0; i < fields.length; i++) {
            types[i] = fields[i].getType();
        }
        return types;
    }
}
