package fr.nivcoo.utilsz.messaging;

import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public interface MessageBackend {
    String getInstanceId();
    JavaPlugin getPlugin();
    void start();
    void close();
    void subscribeRaw(String channel, Consumer<JsonObject> callback);
    void publish(String channel, JsonObject json);
}
