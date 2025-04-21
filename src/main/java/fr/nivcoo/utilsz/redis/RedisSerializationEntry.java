package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

public class RedisSerializationEntry<T extends RedisSerializable> {

    private final RedisTypeAdapter<T> adapter;
    private final RedisHandler<T> handler;

    public RedisSerializationEntry(RedisTypeAdapter<T> adapter, RedisHandler<T> handler) {
        this.adapter = adapter;
        this.handler = handler;
    }

    public void handle(JsonObject json) {
        T obj = adapter.deserialize(json);
        handler.handle(obj);
    }

    public JsonObject serialize(T obj) {
        return adapter.serialize(obj);
    }

    public RedisTypeAdapter<T> getAdapter() {
        return adapter;
    }
}

