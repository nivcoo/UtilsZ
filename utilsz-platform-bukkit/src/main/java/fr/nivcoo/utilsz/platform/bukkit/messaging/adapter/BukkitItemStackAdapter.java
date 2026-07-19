package fr.nivcoo.utilsz.platform.bukkit.messaging.adapter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;

public final class BukkitItemStackAdapter implements BusTypeAdapter<ItemStack> {
    private final Function<byte[], ItemStack> decoder;

    public BukkitItemStackAdapter() {
        this(ItemStack::deserializeBytes);
    }

    BukkitItemStackAdapter(Function<byte[], ItemStack> decoder) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    @Override
    public JsonObject serialize(ItemStack item) {
        Objects.requireNonNull(item, "item");
        byte[] data = item.serializeAsBytes();
        if (data.length == 0) throw new IllegalArgumentException("Serialized item is empty");

        JsonObject json = new JsonObject();
        json.addProperty("value", Base64.getEncoder().encodeToString(data));
        return json;
    }

    @Override
    public ItemStack deserialize(JsonObject json) {
        JsonElement value = json == null ? null : json.get("value");
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Missing or invalid serialized item");
        }

        try {
            byte[] data = Base64.getDecoder().decode(value.getAsString());
            if (data.length == 0) throw new IllegalArgumentException("Serialized item is empty");
            return Objects.requireNonNull(decoder.apply(data), "Decoded item is null");
        } catch (RuntimeException exception) {
            throw new JsonParseException("Invalid serialized item", exception);
        }
    }
}
