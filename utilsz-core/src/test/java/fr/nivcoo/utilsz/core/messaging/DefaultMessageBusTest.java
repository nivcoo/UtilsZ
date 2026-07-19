package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.crypto.AesGcmMessageCrypto;
import fr.nivcoo.utilsz.core.messaging.crypto.MessageCrypto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMessageBusTest {

    private static final AtomicInteger RPC_HANDLES = new AtomicInteger();
    private static final AtomicInteger CLASSIC_EVENT_HANDLES = new AtomicInteger();
    private final List<DefaultMessageBus> buses = new ArrayList<>();

    @BeforeEach
    void resetBackend() {
        InMemoryMessageBackend.reset();
        RPC_HANDLES.set(0);
        CLASSIC_EVENT_HANDLES.set(0);
    }

    @AfterEach
    void closeBuses() {
        buses.forEach(DefaultMessageBus::close);
        buses.clear();
        InMemoryMessageBackend.reset();
    }

    @Test
    void publishDeliversComplexTypedEventToOtherInstance() {
        String channel = channel();
        DefaultMessageBus sender = bus("sender", channel, Runnable::run);
        DefaultMessageBus receiver = bus("receiver", channel, Runnable::run);
        Presence presence = presence("Nivcoo");
        AtomicReference<PresenceEvent> received = new AtomicReference<>();

        receiver.register(PresenceEvent.class, received::set);
        sender.start();
        receiver.start();

        sender.publish(new PresenceEvent(List.of(presence), Map.of("local", presence)));

        assertNotNull(received.get());
        assertInstanceOf(Presence.class, received.get().presences().getFirst());
        assertEquals(presence, received.get().presences().getFirst());
        assertEquals(presence, received.get().byServer().get("local"));
    }

    @Test
    void classRegistrationExecutesClassicMessageOverride() {
        String channel = channel();
        DefaultMessageBus sender = bus("sender", channel, Runnable::run);
        DefaultMessageBus receiver = bus("receiver", channel, Runnable::run);

        receiver.register(ClassicEvent.class);

        sender.publish(new ClassicEvent("handled"));

        assertEquals(1, CLASSIC_EVENT_HANDLES.get());
    }

    @Test
    void handlerRegistrationAcceptsMessageWithoutExecuteOverride() {
        String channel = channel();
        DefaultMessageBus sender = bus("sender", channel, Runnable::run);
        DefaultMessageBus receiver = bus("receiver", channel, Runnable::run);
        AtomicReference<String> handled = new AtomicReference<>();

        receiver.register(HandlerOnlyEvent.class, event -> handled.set(event.value()));

        sender.publish(new HandlerOnlyEvent("injected"));

        assertEquals("injected", handled.get());
    }

    @Test
    void classRegistrationRejectsMessageWithoutExecuteOverride() {
        DefaultMessageBus receiver = bus("receiver", channel(), Runnable::run);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> receiver.register(HandlerOnlyEvent.class)
        );

        assertTrue(error.getMessage().contains(HandlerOnlyEvent.class.getName()));
        assertTrue(error.getMessage().contains("register(Class, BusHandler)"));
    }

    @Test
    void duplicateEventEnvelopeIsIgnoredByMessageId() {
        String channel = channel();
        InMemoryMessageBackend senderBackend = new InMemoryMessageBackend("sender");
        DefaultMessageBus sender = bus(senderBackend, channel, Runnable::run);
        DefaultMessageBus receiver = bus("receiver", channel, Runnable::run);
        AtomicInteger received = new AtomicInteger();

        receiver.register(PresenceEvent.class, event -> received.incrementAndGet());

        sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));
        senderBackend.replayLast(channel);

        assertEquals(1, received.get());
    }

    @Test
    void publishToOnlyDeliversToTargetInstance() {
        String channel = channel();
        DefaultMessageBus sender = bus("sender", channel, Runnable::run);
        DefaultMessageBus target = bus("target", channel, Runnable::run);
        DefaultMessageBus other = bus("other", channel, Runnable::run);
        AtomicInteger targetCount = new AtomicInteger();
        AtomicInteger otherCount = new AtomicInteger();

        target.register(PresenceEvent.class, event -> targetCount.incrementAndGet());
        other.register(PresenceEvent.class, event -> otherCount.incrementAndGet());

        sender.publishTo("target", new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));

        assertEquals(1, targetCount.get());
        assertEquals(0, otherCount.get());
    }

    @Test
    void selfMessagesAreIgnoredUnlessActionAllowsThem() {
        String channel = channel();
        DefaultMessageBus bus = bus("self", channel, Runnable::run);
        AtomicInteger ignored = new AtomicInteger();
        AtomicInteger received = new AtomicInteger();

        bus.register(PresenceEvent.class, event -> ignored.incrementAndGet());
        bus.register(SelfEvent.class, event -> received.incrementAndGet());

        bus.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));
        bus.publish(new SelfEvent("ok"));

        assertEquals(0, ignored.get());
        assertEquals(1, received.get());
    }

    @Test
    void runOnMainThreadQueuesHandlerOnProvidedExecutor() {
        String channel = channel();
        Queue<Runnable> mainThread = new ConcurrentLinkedQueue<>();
        DefaultMessageBus sender = bus("sender", channel, Runnable::run);
        DefaultMessageBus receiver = bus("receiver", channel, mainThread::add);
        AtomicReference<String> handled = new AtomicReference<>();

        receiver.register(MainThreadEvent.class, event -> handled.set(event.value()));

        sender.publish(new MainThreadEvent("queued"));

        assertNull(handled.get());
        assertEquals(1, mainThread.size());

        mainThread.remove().run();

        assertEquals("queued", handled.get());
    }

    @Test
    void rpcRoundTripPreservesNestedRequestAndResponseTypes() throws Exception {
        String channel = channel();
        DefaultMessageBus caller = bus("caller", channel, Runnable::run);
        DefaultMessageBus responder = bus("responder", channel, Runnable::run);
        Presence presence = presence("Nivcoo");

        responder.register(EchoRequest.class);

        CompletableFuture<EchoResponse> future =
                caller.call(new EchoRequest("hello", List.of(presence)), EchoResponse.class);
        EchoResponse response = future.get(1, TimeUnit.SECONDS);

        assertEquals("HELLO", response.value());
        assertInstanceOf(Presence.class, response.presence());
        assertEquals(presence, response.presence());
    }

    @Test
    void rpcErrorResponseCompletesFutureExceptionally() {
        String channel = channel();
        DefaultMessageBus caller = bus("caller", channel, Runnable::run);
        DefaultMessageBus responder = bus("responder", channel, Runnable::run);

        responder.register(FailingRequest.class);

        CompletableFuture<EchoResponse> future = caller.call(new FailingRequest("boom"), EchoResponse.class);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));

        assertTrue(ex.getCause().getMessage().contains("boom"));
    }

    @Test
    void closeCancelsPendingCalls() {
        String channel = channel();
        DefaultMessageBus caller = bus("caller", channel, Runnable::run);

        CompletableFuture<JsonObject> future = caller.callRaw("missing", new JsonObject());
        caller.close();

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void rpcTimeoutCompletesAndIgnoresLateResponses() {
        DefaultMessageBus caller = bus("caller", channel(), Runnable::run);

        CompletableFuture<JsonObject> future =
                caller.callRaw("missing", new JsonObject(), Duration.ofMillis(25));
        ExecutionException error = assertThrows(
                ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS)
        );

        assertInstanceOf(TimeoutException.class, error.getCause());
    }

    @Test
    void duplicateRpcRequestReplaysResponseWithoutRunningHandlerTwice() throws Exception {
        String channel = channel();
        InMemoryMessageBackend callerBackend = new InMemoryMessageBackend("caller");
        InMemoryMessageBackend responderBackend = new InMemoryMessageBackend("responder");
        DefaultMessageBus caller = bus(callerBackend, channel, Runnable::run);
        DefaultMessageBus responder = bus(responderBackend, channel, Runnable::run);
        responder.register(CountingRequest.class);

        EchoResponse response = caller.call(
                new CountingRequest("hello"),
                EchoResponse.class,
                Duration.ofSeconds(1)
        ).get(1, TimeUnit.SECONDS);
        int responsesBeforeReplay = responderBackend.publishCount();

        callerBackend.replayLast(channel);

        assertEquals("HELLO", response.value());
        assertEquals(1, RPC_HANDLES.get());
        assertEquals(responsesBeforeReplay + 1, responderBackend.publishCount());
    }

    @Test
    void constructorDoesNotSubscribeOrStartBackend() {
        LifecycleBackend backend = new LifecycleBackend("lifecycle");
        DefaultMessageBus bus = new DefaultMessageBus(
                backend,
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(bus);
        bus.register(PresenceEvent.class);

        assertEquals(0, backend.subscribeCount.get());
        assertEquals(0, backend.startCount.get());
        assertTrue(bus.callRaw("not-started", new JsonObject()).isCompletedExceptionally());

        bus.start();
        bus.start();

        assertEquals(1, backend.subscribeCount.get());
        assertEquals(1, backend.startCount.get());

        bus.close();
        bus.close();

        assertEquals(1, backend.closeCount.get());
        assertThrows(IllegalStateException.class, bus::start);
    }

    @Test
    void encryptedBusKeepsPayloadOpaqueOnWireAndStillDelivers() {
        String channel = channel();
        byte[] key = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        InMemoryMessageBackend senderBackend = new InMemoryMessageBackend("sender");
        AtomicReference<PresenceEvent> received = new AtomicReference<>();
        DefaultMessageBus sender = bus(senderBackend, channel, Runnable::run, new AesGcmMessageCrypto(key, "test"));
        DefaultMessageBus receiver = bus(new InMemoryMessageBackend("receiver"), channel, Runnable::run, new AesGcmMessageCrypto(key, "test"));

        receiver.register(PresenceEvent.class, received::set);

        sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));

        JsonObject wire = senderBackend.lastPublished();
        assertNotNull(wire);
        assertTrue(wire.getAsJsonObject("payload").get("__encrypted").getAsBoolean());
        assertFalse(wire.getAsJsonObject("payload").has("presences"));
        assertNotNull(received.get());
        assertInstanceOf(Presence.class, received.get().presences().getFirst());
    }

    @Test
    void encryptedMessageIsIgnoredWhenReceiverHasPlaintextBus() {
        String channel = channel();
        byte[] key = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        DefaultMessageBus sender = bus(new InMemoryMessageBackend("sender"), channel, Runnable::run, new AesGcmMessageCrypto(key, "test"));
        DefaultMessageBus receiver = bus("receiver", channel, Runnable::run);
        AtomicInteger received = new AtomicInteger();

        receiver.register(PresenceEvent.class, event -> received.incrementAndGet());

        sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));

        assertEquals(0, received.get());
    }

    @Test
    void failedRequestEncryptionCompletesFutureExceptionally() {
        String channel = channel();
        DefaultMessageBus caller = bus(new InMemoryMessageBackend("caller"), channel, Runnable::run, new FailingCrypto());

        CompletableFuture<JsonObject> future = caller.callRaw("broken", new JsonObject());

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void backendPublishFailureCompletesFutureExceptionally() {
        DefaultMessageBus caller = new DefaultMessageBus(
                new ThrowingBackend("caller"),
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(caller);
        caller.start();

        CompletableFuture<JsonObject> future = caller.callRaw("broken", new JsonObject());

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void backendPublishFailureIsPropagatedForEvents() {
        DefaultMessageBus sender = new DefaultMessageBus(
                new ThrowingBackend("sender"),
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(sender);
        sender.start();

        assertThrows(IllegalStateException.class,
                () -> sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of())));
    }

    private DefaultMessageBus bus(String instanceId, String channel, Consumer<Runnable> mainThreadExecutor) {
        return bus(new InMemoryMessageBackend(instanceId), channel, mainThreadExecutor);
    }

    private DefaultMessageBus bus(InMemoryMessageBackend backend, String channel, Consumer<Runnable> mainThreadExecutor) {
        return bus(backend, channel, mainThreadExecutor, null);
    }

    private DefaultMessageBus bus(InMemoryMessageBackend backend,
                                  String channel,
                                  Consumer<Runnable> mainThreadExecutor,
                                  MessageCrypto crypto) {
        DefaultMessageBus bus = crypto == null
                ? new DefaultMessageBus(backend, channel, mainThreadExecutor, NOPLogger.NOP_LOGGER)
                : new DefaultMessageBus(backend, channel, mainThreadExecutor, NOPLogger.NOP_LOGGER, crypto);
        buses.add(bus);
        bus.start();
        return bus;
    }

    private static String channel() {
        return "test-" + UUID.randomUUID();
    }

    private static Presence presence(String username) {
        return new Presence(UUID.randomUUID(), username, "eden", System.currentTimeMillis());
    }

    private record Presence(UUID uuid, String username, String cluster, long updatedAt) {
    }

    @BusAction("classic-event")
    private record ClassicEvent(String value) implements BusMessage {
        @Override
        public void execute() {
            CLASSIC_EVENT_HANDLES.incrementAndGet();
        }
    }

    @BusAction("handler-only-event")
    private record HandlerOnlyEvent(String value) implements BusMessage {
    }

    @BusAction("presence-event")
    private record PresenceEvent(List<Presence> presences, Map<String, Presence> byServer) implements BusMessage {
        @Override
        public void execute() {
        }
    }

    @BusAction(value = "self-event", receiveOwnMessages = true)
    private record SelfEvent(String value) implements BusMessage {
        @Override
        public void execute() {
        }
    }

    @BusAction(value = "main-thread-event", runOnMainThread = true)
    private record MainThreadEvent(String value) implements BusMessage {
        @Override
        public void execute() {
        }
    }

    @BusAction(value = "echo-request", response = EchoResponse.class)
    private record EchoRequest(String value, List<Presence> presences) implements RpcMessage {
        @Override
        public Object handle() {
            return new EchoResponse(value.toUpperCase(), presences.getFirst());
        }
    }

    private record EchoResponse(String value, Presence presence) {
    }

    @BusAction(value = "counting-request", response = EchoResponse.class)
    private record CountingRequest(String value) implements RpcMessage {
        @Override
        public Object handle() {
            RPC_HANDLES.incrementAndGet();
            return new EchoResponse(value.toUpperCase(), null);
        }
    }

    @BusAction(value = "failing-request", response = EchoResponse.class)
    private record FailingRequest(String message) implements RpcMessage {
        @Override
        public Object handle() {
            throw new IllegalStateException(message);
        }
    }

    private static final class FailingCrypto implements MessageCrypto {
        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public JsonObject encrypt(JsonObject clear, byte[] associatedData) {
            throw new IllegalStateException("encrypt failed");
        }

        @Override
        public JsonObject decrypt(JsonObject encrypted, byte[] associatedData) {
            return encrypted;
        }
    }

    private static final class ThrowingBackend implements MessageBackend {
        private final String instanceId;

        private ThrowingBackend(String instanceId) {
            this.instanceId = instanceId;
        }

        @Override
        public String getInstanceId() {
            return instanceId;
        }

        @Override
        public void start() {
        }

        @Override
        public void close() {
        }

        @Override
        public void subscribeRaw(String channel, Consumer<JsonObject> callback) {
        }

        @Override
        public void publish(String channel, JsonObject json) {
            throw new IllegalStateException("backend failed");
        }

        @Override
        public void onError(Consumer<Throwable> handler) {
        }
    }

    private static final class LifecycleBackend implements MessageBackend {
        private final String instanceId;
        private final AtomicInteger startCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final AtomicInteger subscribeCount = new AtomicInteger();

        private LifecycleBackend(String instanceId) {
            this.instanceId = instanceId;
        }

        @Override
        public String getInstanceId() {
            return instanceId;
        }

        @Override
        public void start() {
            startCount.incrementAndGet();
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }

        @Override
        public void subscribeRaw(String channel, Consumer<JsonObject> callback) {
            subscribeCount.incrementAndGet();
        }

        @Override
        public void publish(String channel, JsonObject json) {
        }

        @Override
        public void onError(Consumer<Throwable> handler) {
        }
    }
}
