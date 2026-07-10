package fr.nivcoo.utilsz.core.messaging.backend;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendSubscribersTest {

    @Test
    void dispatchIsolatesCallbacksAndReportsErrors() {
        BackendSubscribers subscribers = new BackendSubscribers();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        JsonObject message = new JsonObject();
        message.addProperty("value", "before");

        subscribers.add("channel", json -> {
            first.incrementAndGet();
            json.addProperty("value", "mutated");
            throw new IllegalStateException("boom");
        });
        subscribers.add("channel", json -> {
            second.incrementAndGet();
            assertEquals("before", json.get("value").getAsString());
        });

        subscribers.dispatch("channel", message, error::set);

        assertEquals(1, first.get());
        assertEquals(1, second.get());
        assertEquals("boom", error.get().getMessage());
        assertEquals("before", message.get("value").getAsString());
    }
}
