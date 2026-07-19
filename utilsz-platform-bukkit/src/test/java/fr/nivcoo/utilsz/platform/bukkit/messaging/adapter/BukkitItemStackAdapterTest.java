package fr.nivcoo.utilsz.platform.bukkit.messaging.adapter;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.nivcoo.utilsz.core.messaging.BusAdapterRegistry;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;
import fr.nivcoo.utilsz.platform.bukkit.messaging.BukkitMessagingAdapters;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BukkitItemStackAdapterTest {

    @Test
    void roundTripsCompleteSerializedPayloadWithoutModification() {
        byte[] payload = new byte[2_048];
        for (int index = 0; index < payload.length; index++) payload[index] = (byte) (index * 31);
        SerializedItemStack original = new SerializedItemStack(payload);
        BukkitItemStackAdapter adapter = adapter();

        JsonObject json = adapter.serialize(original);
        ItemStack decoded = adapter.deserialize(json);

        assertArrayEquals(payload, Base64.getDecoder().decode(json.get("value").getAsString()));
        assertArrayEquals(payload, decoded.serializeAsBytes());
        assertNotSame(original, decoded);
    }

    @Test
    void reflectiveAdapterRoundTripsNestedItemsAndLists() {
        BukkitItemStackAdapter itemAdapter = adapter();
        BusAdapterRegistry.register(ItemStack.class, itemAdapter);
        try {
            ItemPayload original = new ItemPayload(
                    new SerializedItemStack(new byte[]{1, 2, 3}),
                    List.of(
                            new SerializedItemStack(new byte[]{4, 5}),
                            new SerializedItemStack(new byte[]{6, 7, 8, 9})
                    )
            );
            BusTypeAdapter<ItemPayload> payloadAdapter = BusAdapterRegistry.ensureAdapter(ItemPayload.class);

            ItemPayload decoded = payloadAdapter.deserialize(payloadAdapter.serialize(original));

            assertArrayEquals(original.item().serializeAsBytes(), decoded.item().serializeAsBytes());
            assertArrayEquals(original.items().get(0).serializeAsBytes(),
                    decoded.items().get(0).serializeAsBytes());
            assertArrayEquals(original.items().get(1).serializeAsBytes(),
                    decoded.items().get(1).serializeAsBytes());
        } finally {
            BukkitMessagingAdapters.register();
        }
    }

    @Test
    void bukkitRegistrationProvidesTheItemStackAdapter() {
        BukkitMessagingAdapters.register();

        assertInstanceOf(BukkitItemStackAdapter.class,
                BusAdapterRegistry.getAdapter(ItemStack.class));
    }

    @Test
    void rejectsMissingEmptyAndMalformedPayloads() {
        BukkitItemStackAdapter adapter = adapter();
        JsonObject missing = new JsonObject();
        JsonObject nullValue = new JsonObject();
        nullValue.add("value", JsonNull.INSTANCE);
        JsonObject empty = new JsonObject();
        empty.addProperty("value", "");
        JsonObject malformed = new JsonObject();
        malformed.addProperty("value", "not-base64***");

        assertThrows(JsonParseException.class, () -> adapter.deserialize(null));
        assertThrows(JsonParseException.class, () -> adapter.deserialize(missing));
        assertThrows(JsonParseException.class, () -> adapter.deserialize(nullValue));
        assertThrows(JsonParseException.class, () -> adapter.deserialize(empty));
        assertThrows(JsonParseException.class, () -> adapter.deserialize(malformed));
    }

    @Test
    void wrapsDecoderFailuresAsInvalidMessages() {
        BukkitItemStackAdapter adapter = new BukkitItemStackAdapter(data -> {
            throw new IllegalArgumentException("broken item");
        });
        JsonObject json = new JsonObject();
        json.addProperty("value", Base64.getEncoder().encodeToString(new byte[]{1}));

        assertThrows(JsonParseException.class, () -> adapter.deserialize(json));
    }

    private BukkitItemStackAdapter adapter() {
        return new BukkitItemStackAdapter(SerializedItemStack::new);
    }

    private record ItemPayload(ItemStack item, List<ItemStack> items) {
    }

    private static final class SerializedItemStack extends ItemStack {
        private final byte[] data;

        private SerializedItemStack(byte[] data) {
            this.data = data.clone();
        }

        @Override
        public byte[] serializeAsBytes() {
            return data.clone();
        }

        @Override
        public SerializedItemStack clone() {
            return new SerializedItemStack(data);
        }
    }
}
