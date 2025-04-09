package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

import java.util.function.Function;

class RedisDeserializationEntry<T extends RedisSerializable> {

    private final Function<JsonObject, T> deserializer;
    private final RedisHandler<T> handler;

    public RedisDeserializationEntry(Function<JsonObject, T> deserializer, RedisHandler<T> handler) {
        this.deserializer = deserializer;
        this.handler = handler;
    }

    public void handle(JsonObject json) {
        T obj = deserializer.apply(json);
        handler.handle(obj);
    }
}
