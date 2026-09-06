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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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

        awaitTrue(() -> received.get() != null);
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

        awaitTrue(() -> CLASSIC_EVENT_HANDLES.get() == 1);
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

        awaitTrue(() -> "injected".equals(handled.get()));
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
        awaitTrue(() -> received.get() == 1);
        senderBackend.replayLast(channel);

        assertEquals(1, received.get());
    }

    @Test
    void publishToOnlyDeliversToTargetInstance() {
        String channel = channel();
        InMemoryMessageBackend senderBackend = new InMemoryMessageBackend("sender");
        DefaultMessageBus sender = bus(senderBackend, channel, Runnable::run);
        DefaultMessageBus target = bus("target", channel, Runnable::run);
        DefaultMessageBus other = bus("other", channel, Runnable::run);
        AtomicInteger targetCount = new AtomicInteger();
        AtomicInteger otherCount = new AtomicInteger();

        target.register(PresenceEvent.class, event -> targetCount.incrementAndGet());
        other.register(PresenceEvent.class, event -> otherCount.incrementAndGet());

        sender.publishTo("target", new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));

        awaitTrue(() -> targetCount.get() == 1);
        assertEquals(1, targetCount.get());
        assertEquals(0, otherCount.get());
        assertEquals(1, senderBackend.publishCount());
        assertEquals(1, senderBackend.targetedPublishCount());
        assertEquals("target", senderBackend.lastTarget());
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

        awaitTrue(() -> received.get() == 1);
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

        awaitTrue(() -> mainThread.size() == 1);
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
        awaitTrue(() -> responderBackend.publishCount() == 1);
        int responsesBeforeReplay = responderBackend.publishCount();

        callerBackend.replayLast(channel);

        awaitTrue(() -> responderBackend.publishCount() >= responsesBeforeReplay + 1);
        assertEquals("HELLO", response.value());
        assertEquals(1, RPC_HANDLES.get());
        assertEquals(responsesBeforeReplay + 1, responderBackend.publishCount());
    }

    @Test
    void duplicateRpcWithoutReplayDoesNotRetainOrResendResponse() throws Exception {
        String channel = channel();
        InMemoryMessageBackend callerBackend = new InMemoryMessageBackend("caller");
        InMemoryMessageBackend responderBackend = new InMemoryMessageBackend("responder");
        DefaultMessageBus caller = bus(callerBackend, channel, Runnable::run);
        DefaultMessageBus responder = bus(responderBackend, channel, Runnable::run);
        responder.register(NonReplayRequest.class);

        EchoResponse response = caller.call(
                new NonReplayRequest("hello"),
                EchoResponse.class,
                Duration.ofSeconds(1)
        ).get(1, TimeUnit.SECONDS);
        awaitTrue(() -> responderBackend.publishCount() == 1);

        callerBackend.replayLast(channel);

        responder.publish(new PresenceEvent(List.of(presence("marker")), Map.of()));
        awaitTrue(() -> responderBackend.publishCount() >= 2);

        assertEquals("HELLO", response.value());
        assertEquals(1, RPC_HANDLES.get());
        assertEquals(2, responderBackend.publishCount());
    }

    @Test
    void asynchronousRpcSerializesCompletedValue() throws Exception {
        String channel = channel();
        DefaultMessageBus caller = bus("caller", channel, Runnable::run);
        DefaultMessageBus responder = bus("responder", channel, Runnable::run);
        responder.register(AsyncRequest.class);

        EchoResponse response = caller.call(
                new AsyncRequest("hello"),
                EchoResponse.class,
                Duration.ofSeconds(1)
        ).get(1, TimeUnit.SECONDS);

        assertEquals("HELLO", response.value());
    }

    @Test
    void asynchronousRpcPropagatesFailure() {
        String channel = channel();
        DefaultMessageBus caller = bus("caller", channel, Runnable::run);
        DefaultMessageBus responder = bus("responder", channel, Runnable::run);
        responder.register(AsyncFailingRequest.class);

        ExecutionException error = assertThrows(ExecutionException.class, () -> caller.call(
                new AsyncFailingRequest("broken"),
                EchoResponse.class,
                Duration.ofSeconds(1)
        ).get(1, TimeUnit.SECONDS));

        assertEquals("broken", error.getCause().getMessage());
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

        awaitTrue(() -> senderBackend.publishCount() == 1 && received.get() != null);
        JsonObject wire = senderBackend.lastPublished();
        assertNotNull(wire);
        assertTrue(wire.getAsJsonObject("payload").get("__encrypted").getAsBoolean());
        assertFalse(wire.getAsJsonObject("payload").has("presences"));
        assertNotNull(received.get());
        assertInstanceOf(Presence.class, received.get().presences().getFirst());
    }

    @Test
    void encryptedTargetedRpcSkipsDecryptOnBystander() throws Exception {
        String channel = channel();
        byte[] key = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        InMemoryMessageBackend callerBackend = new InMemoryMessageBackend("caller");
        InMemoryMessageBackend responderBackend = new InMemoryMessageBackend("responder");
        CountingCrypto callerCrypto = new CountingCrypto(new AesGcmMessageCrypto(key, "test"));
        CountingCrypto responderCrypto = new CountingCrypto(new AesGcmMessageCrypto(key, "test"));
        CountingCrypto bystanderCrypto = new CountingCrypto(new AesGcmMessageCrypto(key, "test"));
        DefaultMessageBus caller = bus(callerBackend, channel, Runnable::run, callerCrypto);
        DefaultMessageBus responder = bus(responderBackend, channel, Runnable::run, responderCrypto);
        bus(new InMemoryMessageBackend("bystander"), channel, Runnable::run, bystanderCrypto);
        Presence presence = presence("Nivcoo");

        responder.register(EchoRequest.class);

        EchoResponse response = caller.callTo(
                "responder",
                new EchoRequest("hello", List.of(presence)),
                EchoResponse.class
        ).get(1, TimeUnit.SECONDS);
        awaitTrue(() -> callerBackend.publishCount() == 1 && responderBackend.publishCount() == 1);

        assertEquals("HELLO", response.value());
        assertEquals(presence, response.presence());
        assertEquals(1, callerCrypto.decryptCount());
        assertEquals(1, responderCrypto.decryptCount());
        assertEquals(0, bystanderCrypto.decryptCount());
        assertEquals(1, callerBackend.publishCount());
        assertEquals(1, callerBackend.targetedPublishCount());
        assertEquals(1, responderBackend.publishCount());
        assertEquals(1, responderBackend.targetedPublishCount());
    }

    @Test
    void encryptedMessageIsIgnoredWhenReceiverHasPlaintextBus() {
        String channel = channel();
        byte[] key = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        InMemoryMessageBackend senderBackend = new InMemoryMessageBackend("sender");
        DefaultMessageBus sender = bus(senderBackend, channel, Runnable::run, new AesGcmMessageCrypto(key, "test"));
        DefaultMessageBus receiver = bus("receiver", channel, Runnable::run);
        AtomicInteger received = new AtomicInteger();

        receiver.register(PresenceEvent.class, event -> received.incrementAndGet());

        sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));

        awaitTrue(() -> senderBackend.publishCount() == 1);
        assertEquals(0, received.get());
    }

    @Test
    void failedRequestEncryptionCompletesFutureExceptionally() {
        String channel = channel();
        DefaultMessageBus caller = bus(new InMemoryMessageBackend("caller"), channel, Runnable::run, new FailingCrypto());

        CompletableFuture<JsonObject> future = caller.callRaw("broken", new JsonObject());

        assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
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

        CompletableFuture<JsonObject> future = caller.callRaw(
                "broken", new JsonObject(), Duration.ofMillis(25L));

        assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void messageWaitsForBackendRecoveryWithoutBlockingCaller() {
        DelayedReadyBackend backend = new DelayedReadyBackend("sender");
        DefaultMessageBus sender = new DefaultMessageBus(
                backend,
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(sender);
        sender.start();

        assertTimeoutPreemptively(Duration.ofMillis(250),
                () -> sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of())));
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50L));
        assertEquals(0, backend.publishCount.get());
        backend.ready.set(true);
        awaitTrue(() -> backend.publishCount.get() == 1, Duration.ofSeconds(5L));

        assertEquals(1, backend.publishCount.get());
    }

    @Test
    void backendPublishFailureDoesNotEscapeOrRetryEvent() {
        ThrowingBackend backend = new ThrowingBackend("sender");
        DefaultMessageBus sender = new DefaultMessageBus(
                backend,
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(sender);
        sender.start();

        assertDoesNotThrow(
                () -> sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of())));
        awaitTrue(() -> backend.publishCount.get() == 1);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(600L));
        assertEquals(1, backend.publishCount.get());
    }

    @Test
    void eventPublishDoesNotWaitForBlockedBackend() throws Exception {
        BlockingBackend backend = new BlockingBackend("sender");
        DefaultMessageBus sender = new DefaultMessageBus(
                backend,
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(sender);
        sender.start();

        assertTimeoutPreemptively(Duration.ofMillis(250),
                () -> sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of())));
        assertTrue(backend.entered.await(1, TimeUnit.SECONDS));
        backend.release.countDown();
    }

    @Test
    void expiredRpcIsNotSentAfterOutboundWorkerRecovers() throws Exception {
        BlockingBackend backend = new BlockingBackend("sender");
        DefaultMessageBus sender = new DefaultMessageBus(
                backend,
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(sender);
        sender.start();

        sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));
        assertTrue(backend.entered.await(1, TimeUnit.SECONDS));
        CompletableFuture<JsonObject> expired =
                sender.callRaw("expired", new JsonObject(), Duration.ofMillis(25L));
        ExecutionException failure =
                assertThrows(ExecutionException.class, () -> expired.get(1, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, failure.getCause());

        backend.release.countDown();
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50L));

        assertEquals(1, backend.publishCount.get());
    }

    @Test
    void queuedRawRpcUsesPayloadSnapshotFromCallTime() throws Exception {
        BlockingBackend backend = new BlockingBackend("sender");
        DefaultMessageBus sender = new DefaultMessageBus(
                backend,
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(sender);
        sender.start();

        sender.publish(new PresenceEvent(List.of(presence("Nivcoo")), Map.of()));
        assertTrue(backend.entered.await(1, TimeUnit.SECONDS));
        JsonObject payload = new JsonObject();
        payload.addProperty("value", "before");
        CompletableFuture<JsonObject> call =
                sender.callRaw("snapshot", payload, Duration.ofSeconds(1L));
        payload.addProperty("value", "after");
        backend.release.countDown();
        awaitTrue(() -> backend.published.size() == 2);

        JsonObject wire = backend.published.stream()
                .filter(message -> "snapshot".equals(message.get("action").getAsString()))
                .findFirst()
                .orElseThrow();
        assertEquals("before", wire.getAsJsonObject("payload").get("value").getAsString());
        call.cancel(false);
    }

    @Test
    void closeDrainsQueuedEventsBeforeClosingBackend() throws Exception {
        BlockingBackend backend = new BlockingBackend("sender");
        DefaultMessageBus sender = new DefaultMessageBus(
                backend,
                channel(),
                Runnable::run,
                NOPLogger.NOP_LOGGER
        );
        buses.add(sender);
        sender.start();

        sender.publish(new PresenceEvent(List.of(presence("first")), Map.of()));
        assertTrue(backend.entered.await(1, TimeUnit.SECONDS));
        sender.publish(new PresenceEvent(List.of(presence("last")), Map.of()));
        backend.release.countDown();

        sender.close();

        assertEquals(2, backend.publishCount.get());
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

    private static void awaitTrue(BooleanSupplier condition) {
        awaitTrue(condition, Duration.ofSeconds(1L));
    }

    private static void awaitTrue(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
        }
        assertTrue(condition.getAsBoolean());
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

    @BusAction(value = "non-replay-request", response = EchoResponse.class, replayResponse = false)
    private record NonReplayRequest(String value) implements RpcMessage {
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

    @BusAction(value = "async-request", response = EchoResponse.class)
    private record AsyncRequest(String value) implements RpcMessage {
        @Override
        public Object handle() {
            return CompletableFuture.completedFuture(
                    new EchoResponse(value.toUpperCase(), null));
        }
    }

    @BusAction(value = "async-failing-request", response = EchoResponse.class)
    private record AsyncFailingRequest(String message) implements RpcMessage {
        @Override
        public Object handle() {
            return CompletableFuture.failedFuture(new IllegalStateException(message));
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

    private static final class CountingCrypto implements MessageCrypto {
        private final MessageCrypto delegate;
        private final AtomicInteger decryptCount = new AtomicInteger();

        private CountingCrypto(MessageCrypto delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean enabled() {
            return delegate.enabled();
        }

        @Override
        public JsonObject encrypt(JsonObject clear, byte[] associatedData) throws Exception {
            return delegate.encrypt(clear, associatedData);
        }

        @Override
        public JsonObject decrypt(JsonObject encrypted, byte[] associatedData) throws Exception {
            decryptCount.incrementAndGet();
            return delegate.decrypt(encrypted, associatedData);
        }

        private int decryptCount() {
            return decryptCount.get();
        }
    }

    private static final class ThrowingBackend implements MessageBackend {
        private final String instanceId;
        private final AtomicInteger publishCount = new AtomicInteger();

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
            publishCount.incrementAndGet();
            throw new IllegalStateException("backend failed");
        }

        @Override
        public void onError(Consumer<Throwable> handler) {
        }
    }

    private static final class DelayedReadyBackend implements MessageBackend {
        private final String instanceId;
        private final AtomicBoolean ready = new AtomicBoolean(false);
        private final AtomicInteger publishCount = new AtomicInteger();

        private DelayedReadyBackend(String instanceId) {
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
        public boolean ready() {
            return ready.get();
        }

        @Override
        public void publish(String channel, JsonObject json) {
            publishCount.incrementAndGet();
        }

        @Override
        public void onError(Consumer<Throwable> handler) {
        }
    }

    private static final class BlockingBackend implements MessageBackend {
        private final String instanceId;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger publishCount = new AtomicInteger();
        private final Queue<JsonObject> published = new ConcurrentLinkedQueue<>();

        private BlockingBackend(String instanceId) {
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
            release.countDown();
        }

        @Override
        public void subscribeRaw(String channel, Consumer<JsonObject> callback) {
        }

        @Override
        public void publish(String channel, JsonObject json) {
            publishCount.incrementAndGet();
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            published.add(json.deepCopy());
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
