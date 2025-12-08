package fr.nivcoo.utilsz.messaging;

import com.google.gson.JsonObject;

public interface BusTypeAdapter<T> {
    JsonObject serialize(T value);
    T deserialize(JsonObject json);
}
