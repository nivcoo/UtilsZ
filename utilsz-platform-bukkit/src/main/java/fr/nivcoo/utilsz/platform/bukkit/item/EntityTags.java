package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public final class EntityTags {

    private final Plugin plugin;

    public EntityTags(Plugin plugin) {
        this.plugin = plugin;
    }

    public static EntityTags of(Plugin plugin) {
        return new EntityTags(plugin);
    }

    public NamespacedKey key(String key) {
        return new NamespacedKey(plugin, key);
    }

    public void setString(PersistentDataHolder holder, String key, String value) {
        if (holder != null) holder.getPersistentDataContainer().set(key(key), PersistentDataType.STRING, value);
    }

    public void setInt(PersistentDataHolder holder, String key, int value) {
        if (holder != null) holder.getPersistentDataContainer().set(key(key), PersistentDataType.INTEGER, value);
    }

    public void setBoolean(PersistentDataHolder holder, String key, boolean value) {
        if (holder != null) holder.getPersistentDataContainer().set(key(key), PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }

    public void setEnum(PersistentDataHolder holder, String key, Enum<?> value) {
        if (value == null) remove(holder, key);
        else setString(holder, key, value.name());
    }

    public Optional<String> getString(PersistentDataHolder holder, String key) {
        return holder == null ? Optional.empty() : Optional.ofNullable(holder.getPersistentDataContainer().get(key(key), PersistentDataType.STRING));
    }

    public Optional<Integer> getInt(PersistentDataHolder holder, String key) {
        return holder == null ? Optional.empty() : Optional.ofNullable(holder.getPersistentDataContainer().get(key(key), PersistentDataType.INTEGER));
    }

    public Optional<Boolean> getBoolean(PersistentDataHolder holder, String key) {
        if (holder == null) return Optional.empty();
        Byte value = holder.getPersistentDataContainer().get(key(key), PersistentDataType.BYTE);
        return value == null ? Optional.empty() : Optional.of(value != 0);
    }

    public <E extends Enum<E>> Optional<E> getEnum(PersistentDataHolder holder, String key, Class<E> type) {
        return getString(holder, key).flatMap(value -> {
            try {
                return Optional.of(Enum.valueOf(type, value));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        });
    }

    public boolean has(PersistentDataHolder holder, String key) {
        return holder != null && holder.getPersistentDataContainer().getKeys().contains(key(key));
    }

    public boolean hasString(PersistentDataHolder holder, String key) {
        return holder != null && holder.getPersistentDataContainer().has(key(key), PersistentDataType.STRING);
    }

    public boolean hasInt(PersistentDataHolder holder, String key) {
        return holder != null && holder.getPersistentDataContainer().has(key(key), PersistentDataType.INTEGER);
    }

    public void remove(PersistentDataHolder holder, String key) {
        if (holder != null) holder.getPersistentDataContainer().remove(key(key));
    }
}
