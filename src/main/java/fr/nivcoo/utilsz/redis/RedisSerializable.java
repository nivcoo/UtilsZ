package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

import java.lang.reflect.RecordComponent;


public interface RedisSerializable {
    void execute();

    default JsonObject toJson() {
        RedisMessage message = new RedisMessage(getAction());

        if (this.getClass().isRecord()) {
            RecordComponent[] components = this.getClass().getRecordComponents();
            for (RecordComponent component : components) {
                try {
                    Object value = component.getAccessor().invoke(this);
                    if (value != null) {
                        message.add(component.getName(), value);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Error serializing record component: " + component.getName(), e);
                }
            }
        }

        return message.toJson();
    }


    default String getAction() {
        RedisAction annotation = this.getClass().getAnnotation(RedisAction.class);

        return annotation != null ? annotation.value() : "unknown";
    }
}
