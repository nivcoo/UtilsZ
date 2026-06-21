package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

@SuppressWarnings("unused")
public final class ItemTags {

    private final Plugin plugin;

    public ItemTags(Plugin plugin) {
        this.plugin = plugin;
    }

    public static ItemTags of(Plugin plugin) {
        return new ItemTags(plugin);
    }

    public NamespacedKey key(String key) {
        return new NamespacedKey(plugin, key);
    }

    public ItemStack setString(ItemStack item, String key, String value) {
        return edit(item, meta -> meta.getPersistentDataContainer().set(key(key), PersistentDataType.STRING, value));
    }

    public ItemStack setInt(ItemStack item, String key, int value) {
        return edit(item, meta -> meta.getPersistentDataContainer().set(key(key), PersistentDataType.INTEGER, value));
    }

    public ItemStack setBoolean(ItemStack item, String key, boolean value) {
        return edit(item, meta -> meta.getPersistentDataContainer().set(key(key), PersistentDataType.BYTE, (byte) (value ? 1 : 0)));
    }

    public ItemStack setEnum(ItemStack item, String key, Enum<?> value) {
        return value == null ? remove(item, key) : setString(item, key, value.name());
    }

    public Optional<String> getString(ItemStack item, String key) {
        ItemMeta meta = meta(item);
        return meta == null ? Optional.empty() : Optional.ofNullable(meta.getPersistentDataContainer().get(key(key), PersistentDataType.STRING));
    }

    public Optional<Integer> getInt(ItemStack item, String key) {
        ItemMeta meta = meta(item);
        return meta == null ? Optional.empty() : Optional.ofNullable(meta.getPersistentDataContainer().get(key(key), PersistentDataType.INTEGER));
    }

    public Optional<Boolean> getBoolean(ItemStack item, String key) {
        ItemMeta meta = meta(item);
        if (meta == null) return Optional.empty();
        Byte value = meta.getPersistentDataContainer().get(key(key), PersistentDataType.BYTE);
        return value == null ? Optional.empty() : Optional.of(value != 0);
    }

    public <E extends Enum<E>> Optional<E> getEnum(ItemStack item, String key, Class<E> type) {
        return getString(item, key).flatMap(value -> {
            try {
                return Optional.of(Enum.valueOf(type, value));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        });
    }

    public boolean has(ItemStack item, String key) {
        ItemMeta meta = meta(item);
        return meta != null && meta.getPersistentDataContainer().getKeys().contains(key(key));
    }

    public boolean hasString(ItemStack item, String key) {
        ItemMeta meta = meta(item);
        return meta != null && meta.getPersistentDataContainer().has(key(key), PersistentDataType.STRING);
    }

    public boolean hasInt(ItemStack item, String key) {
        ItemMeta meta = meta(item);
        return meta != null && meta.getPersistentDataContainer().has(key(key), PersistentDataType.INTEGER);
    }

    public ItemStack remove(ItemStack item, String key) {
        return edit(item, meta -> meta.getPersistentDataContainer().remove(key(key)));
    }

    private ItemStack edit(ItemStack item, MetaEdit edit) {
        ItemMeta meta = meta(item);
        if (meta == null) return item;
        edit.apply(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemMeta meta(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        return item.getItemMeta();
    }

    @FunctionalInterface
    private interface MetaEdit {
        void apply(ItemMeta meta);
    }
}
