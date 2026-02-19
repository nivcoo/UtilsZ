package fr.nivcoo.utilsz.core.messaging.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.nivcoo.utilsz.core.messaging.MessageBackend;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.jetbrains.annotations.NotNull;

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

    private static final String CONNECTION_NAME = "MessageBusRabbitMQ";

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

    private volatile Consumer<Throwable> errorHandler;

    private Connection connection;
    private Channel channel;

    public RabbitMqMessageBackend(String host,
                                  int port,
                                  String virtualHost,
                                  String username,
                                  String password) {
        this.host = host;
        this.port = port;
        this.virtualHost = virtualHost;
        this.username = username;
        this.password = password;
    }

    @Override
    public void onError(Consumer<Throwable> handler) {
        this.errorHandler = handler;
    }

    private void report(Throwable t) {
        Consumer<Throwable> h = this.errorHandler;
        if (h != null) h.accept(t);
    }

    @Override
    public String getInstanceId() {
        return instanceId;
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
            if (channel == null) return;
            try {
                channel.exchangeDeclare(channelName, BuiltinExchangeType.FANOUT, true);

                byte[] body = json == null
                        ? "{}".getBytes(StandardCharsets.UTF_8)
                        : json.toString().getBytes(StandardCharsets.UTF_8);

                channel.basicPublish(channelName, "", null, body);
            } catch (IOException e) {
                report(e);
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
            ConnectionFactory factory = getConnectionFactory();
            connection = factory.newConnection(CONNECTION_NAME);
            channel = connection.createChannel();
        } catch (Exception e) {
            report(e);
            connection = null;
            channel = null;
        }
    }

    private @NotNull ConnectionFactory getConnectionFactory() {
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

        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(2000);
        factory.setTopologyRecoveryEnabled(true);

        return factory;
    }

    private void ensureSubscription(String channelName) {
        if (subscribedChannels.containsKey(channelName)) return;
        if (channel == null || !channel.isOpen()) return;

        try {
            channel.exchangeDeclare(channelName, BuiltinExchangeType.FANOUT, true);

            String queue = channelName + "." + instanceId;
            channel.queueDeclare(queue, false, true, true, null);
            channel.queueBind(queue, channelName, "");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);

                JsonObject obj;
                try {
                    obj = JsonParser.parseString(body).getAsJsonObject();
                } catch (Exception e) {
                    report(e);
                    return;
                }

                List<Consumer<JsonObject>> regs = subscribers.get(channelName);
                if (regs == null) return;

                List<Consumer<JsonObject>> copy;
                synchronized (regs) {
                    copy = new ArrayList<>(regs);
                }

                for (Consumer<JsonObject> cb : copy) {
                    try {
                        cb.accept(obj);
                    } catch (Throwable t) {
                        report(t);
                    }
                }
            };

            channel.basicConsume(queue, true, deliverCallback, consumerTag -> {});
            subscribedChannels.put(channelName, Boolean.TRUE);

        } catch (IOException e) {
            report(e);
        }
    }
}
