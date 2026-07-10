package fr.nivcoo.utilsz.core.messaging;

import fr.nivcoo.utilsz.core.messaging.adapter.ListAdapter;
import fr.nivcoo.utilsz.core.messaging.adapter.MapAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingAdapterTest {

    @Test
    void ensureAdapterInitializesBuiltinsBeforeReflectiveFallback() {
        assertNotNull(BusAdapterRegistry.ensureAdapter(String.class));
        assertEquals("hello", BusAdapterRegistry.ensureAdapter(String.class)
                .deserialize(BusAdapterRegistry.ensureAdapter(String.class).serialize("hello")));
        assertEquals(UUID.class, BusAdapterRegistry.ensureAdapter(UUID.class)
                .deserialize(BusAdapterRegistry.ensureAdapter(UUID.class).serialize(UUID.randomUUID()))
                .getClass());
    }

    @Test
    void noopBusCompletesCallsAsFailedFutures() {
        NoopMessageBus bus = new NoopMessageBus();

        ExecutionException rawError = assertThrows(ExecutionException.class,
                () -> bus.callRaw("noop", new com.google.gson.JsonObject()).get());
        ExecutionException targetedError = assertThrows(ExecutionException.class,
                () -> bus.callRawTo("target", "noop", new com.google.gson.JsonObject()).get());

        assertTrue(rawError.getCause().getMessage().contains("Messaging disabled"));
        assertTrue(targetedError.getCause().getMessage().contains("Messaging disabled"));
    }

    @Test
    void reflectiveAdapterPreservesDeclaredGenericCollectionTypes() {
        BusTypeAdapter<ComplexPayload> adapter = BusAdapterRegistry.ensureAdapter(ComplexPayload.class);
        Presence first = presence("Nivcoo", PresenceState.ONLINE);
        Presence second = presence("Alex", PresenceState.OFFLINE);

        ArrayList<Presence> concreteList = new ArrayList<>();
        concreteList.add(first);
        concreteList.add(second);

        HashMap<UUID, Presence> byUuid = new HashMap<>();
        byUuid.put(first.uuid(), first);
        byUuid.put(second.uuid(), second);

        Set<Presence> unique = new LinkedHashSet<>();
        unique.add(first);
        unique.add(second);

        ComplexPayload payload = new ComplexPayload(
                first,
                List.of(first, second),
                concreteList,
                Map.of("first", first, "second", second),
                byUuid,
                Map.of("cluster-a", List.of(first, second)),
                unique
        );

        ComplexPayload copy = adapter.deserialize(adapter.serialize(payload));

        assertEquals(first, copy.direct());
        assertInstanceOf(Presence.class, copy.presences().get(0));
        assertEquals(List.of(first, second), copy.presences());
        assertInstanceOf(ArrayList.class, copy.concreteList());
        assertEquals(concreteList, copy.concreteList());
        assertInstanceOf(Presence.class, copy.byName().get("first"));
        assertEquals(first, copy.byName().get("first"));
        assertInstanceOf(HashMap.class, copy.byUuid());
        assertEquals(second, copy.byUuid().get(second.uuid()));
        assertInstanceOf(Presence.class, copy.grouped().get("cluster-a").get(0));
        assertEquals(List.of(first, second), copy.grouped().get("cluster-a"));
        assertEquals(unique, copy.unique());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rawListAdapterKeepsElementTypesWhenRuntimeTypeIsKnown() {
        Presence presence = presence("Nivcoo", PresenceState.ONLINE);
        ListAdapter adapter = new ListAdapter();

        List copy = adapter.deserialize(adapter.serialize(List.of(presence)));

        assertInstanceOf(Presence.class, copy.getFirst());
        assertEquals(presence, copy.getFirst());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rawMapAdapterKeepsValueTypesWhenRuntimeTypeIsKnown() {
        Presence presence = presence("Nivcoo", PresenceState.ONLINE);
        MapAdapter adapter = new MapAdapter();

        Map copy = adapter.deserialize(adapter.serialize(Map.of("presence", presence)));

        assertInstanceOf(Presence.class, copy.get("presence"));
        assertEquals(presence, copy.get("presence"));
    }

    private static Presence presence(String username, PresenceState state) {
        return new Presence(UUID.randomUUID(), username, state, System.currentTimeMillis());
    }

    private enum PresenceState {
        ONLINE,
        OFFLINE
    }

    private record Presence(UUID uuid, String username, PresenceState state, long updatedAt) {
    }

    @BusAction("complex-payload")
    private record ComplexPayload(Presence direct,
                                  List<Presence> presences,
                                  ArrayList<Presence> concreteList,
                                  Map<String, Presence> byName,
                                  HashMap<UUID, Presence> byUuid,
                                  Map<String, List<Presence>> grouped,
                                  Set<Presence> unique) implements BusMessage {

        @Override
        public void execute() {
        }
    }
}
