package fr.nivcoo.utilsz.messaging.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.nivcoo.utilsz.messaging.BusAdapterRegistry;
import fr.nivcoo.utilsz.messaging.MessageBackend;
import org.bukkit.plugin.java.JavaPlugin;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RabbitMqMessageBackend implements MessageBackend {

    private static final String EXCHANGE_NAME = "utilz.messaging";

    private final JavaPlugin plugin;
    private final String instanceId = UUID.randomUUID().toString();

    private final String host;
    private final int port;
    private final String virtualHost;
    private final String username;
    private final String password;

    private final Map<String, List<Consumer<JsonObject>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> subscribedChannels = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lock = new Object();

    private Connection connection;
    private Channel channel;

    public RabbitMqMessageBackend(JavaPlugin plugin,
                                  String host,
                                  int port,
                                  String virtualHost,
                                  String username,
                                  String password) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.virtualHost = virtualHost;
        this.username = username;
        this.password = password;
        BusAdapterRegistry.registerBuiltins();
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        synchronized (lock) {
            ensureConnection();
            for (String ch : subscribers.keySet()) {
                ensureSubscription(ch);
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        synchronized (lock) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException | TimeoutException ignored) {
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
            channel = null;
            connection = null;
            subscribedChannels.clear();
        }
    }

    @Override
    public void subscribeRaw(String channelName, Consumer<JsonObject> callback) {
        subscribers
                .computeIfAbsent(channelName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(callback);

        if (!running.get()) return;
        synchronized (lock) {
            ensureConnection();
            ensureSubscription(channelName);
        }
    }

    @Override
    public void publish(String channelName, JsonObject json) {
        synchronized (lock) {
            if (!running.get()) return;
            ensureConnection();
            try {
                channel.basicPublish(EXCHANGE_NAME, channelName, null, json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                plugin.getLogger().warning("[Messaging RabbitMQ] Publish failed on " + channelName + ": " + e.getMessage());
            }
        }
    }

    private void ensureConnection() {
        if (connection != null && connection.isOpen() && channel != null && channel.isOpen()) return;
        try {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            if (virtualHost != null && !virtualHost.isEmpty()) {
                factory.setVirtualHost(virtualHost);
            }
            if (username != null && !username.isEmpty()) {
                factory.setUsername(username);
            }
            if (password != null && !password.isEmpty()) {
                factory.setPassword(password);
            }
            connection = factory.newConnection("UtilzMessagingRabbit");
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);
        } catch (Exception e) {
            plugin.getLogger().warning("[Messaging RabbitMQ] Connection failed: " + e.getMessage());
            connection = null;
            channel = null;
        }
    }

    private void ensureSubscription(String channelName) {
        if (subscribedChannels.containsKey(channelName)) return;
        if (channel == null || !channel.isOpen()) return;

        try {
            String queue = "utilz." + channelName + "." + instanceId;
            channel.queueDeclare(queue, false, true, true, null);
            channel.queueBind(queue, EXCHANGE_NAME, channelName);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                JsonObject obj;
                try {
                    obj = JsonParser.parseString(body).getAsJsonObject();
                } catch (Exception e) {
                    plugin.getLogger().warning("[Messaging RabbitMQ] Invalid JSON: " + e.getMessage());
                    return;
                }
                List<Consumer<JsonObject>> regs = subscribers.get(channelName);
                if (regs == null) return;

                Consumer<JsonObject>[] copy;
                synchronized (regs) {
                    copy = regs.toArray(new Consumer[0]);
                }

                for (Consumer<JsonObject> cb : copy) {
                    try {
                        cb.accept(obj);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[Messaging RabbitMQ] Subscriber error: " + t.getMessage());
                    }
                }
            };

            channel.basicConsume(queue, true, deliverCallback, consumerTag -> {
            });
            subscribedChannels.put(channelName, Boolean.TRUE);
        } catch (IOException e) {
            plugin.getLogger().warning("[Messaging RabbitMQ] Subscription failed on " + channelName + ": " + e.getMessage());
        }
    }
}
