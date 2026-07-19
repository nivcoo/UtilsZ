package fr.nivcoo.utilsz.core.messaging.backend;

import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RabbitMqMessageBackendTest {

    @Test
    void publishFailsWhenBackendIsNotStarted() {
        RabbitMqMessageBackend backend = new RabbitMqMessageBackend(
                "localhost", 5672, "/", "guest", "guest");

        assertThrows(IllegalStateException.class,
                () -> backend.publish("auction-events", new JsonObject()));
    }

    @Test
    void startFailsWhenInitialConnectionCannotBeEstablished() {
        RabbitMqMessageBackend backend = new RabbitMqMessageBackend(
                "localhost", 5672, "/", "guest", "guest",
                () -> { throw new IOException("offline"); });

        assertThrows(IllegalStateException.class, backend::start);
        assertThrows(IllegalStateException.class,
                () -> backend.publish("auction-events", new JsonObject()));
    }

    @Test
    void reconnectRecreatesEverySubscriptionBeforePublishing() {
        AtomicInteger bindings = new AtomicInteger();
        AtomicInteger confirms = new AtomicInteger();
        AtomicBoolean firstOpen = new AtomicBoolean(true);
        AtomicBoolean secondOpen = new AtomicBoolean(true);
        Connection first = connection(firstOpen, channel(firstOpen, bindings, confirms));
        Connection second = connection(secondOpen, channel(secondOpen, bindings, confirms));
        List<Connection> connections = List.of(first, second);
        AtomicInteger connectionIndex = new AtomicInteger();
        RabbitMqMessageBackend backend = new RabbitMqMessageBackend(
                "localhost", 5672, "/", "guest", "guest",
                () -> connections.get(connectionIndex.getAndIncrement()));

        backend.subscribeRaw("auction-events", ignored -> { });
        backend.start();
        assertEquals(1, bindings.get());

        firstOpen.set(false);
        backend.publish("auction-events", new JsonObject());

        assertEquals(2, connectionIndex.get());
        assertEquals(2, bindings.get());
        assertEquals(1, confirms.get());
        backend.close();
    }

    private Channel channel(
            AtomicBoolean open,
            AtomicInteger bindings,
            AtomicInteger confirms
    ) {
        return (Channel) Proxy.newProxyInstance(
                Channel.class.getClassLoader(), new Class<?>[]{Channel.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "isOpen" -> open.get();
                        case "close" -> { open.set(false); yield null; }
                        case "queueBind" -> { bindings.incrementAndGet(); yield null; }
                        case "waitForConfirmsOrDie" -> { confirms.incrementAndGet(); yield null; }
                        case "basicConsume" -> "consumer";
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private Connection connection(AtomicBoolean open, Channel channel) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "isOpen" -> open.get();
                        case "createChannel" -> channel;
                        case "close" -> { open.set(false); yield null; }
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
