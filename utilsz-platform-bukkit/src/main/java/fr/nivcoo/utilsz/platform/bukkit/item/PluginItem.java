package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Optional;

@SuppressWarnings("unused")
public abstract class PluginItem<T> {

    public static final String ITEM_ID_TAG = "utilsz-item-id";

    private final JavaPlugin plugin;
    private final ItemTags tags;

    protected PluginItem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tags = ItemTags.of(plugin);
    }

    public abstract String id();

    protected abstract ItemStack buildItem(T data);

    protected abstract void writeData(ItemStack item, T data);

    protected abstract Optional<T> readData(ItemStack item);

    public final ItemStack create(T data) {
        ItemStack item = buildItem(data);
        if (item == null) throw new IllegalStateException("PluginItem " + id() + " built a null ItemStack.");
        tags.setString(item, ITEM_ID_TAG, id());
        writeData(item, data);
        return item;
    }

    public HashMap<Integer, ItemStack> give(Player player, T data) {
        return give(player, data, 1);
    }

    public HashMap<Integer, ItemStack> give(Player player, T data, int amount) {
        return ItemDelivery.give(player, create(data), amount);
    }

    public void giveOrDrop(Player player, T data) {
        giveOrDrop(player, data, 1);
    }

    public void giveOrDrop(Player player, T data, int amount) {
        ItemDelivery.giveOrDrop(player, create(data), amount);
    }

    public void drop(Location location, T data) {
        drop(location, data, 1);
    }

    public void drop(Location location, T data, int amount) {
        ItemDelivery.drop(location, create(data), amount);
    }

    public void dropAtBlock(Location location, T data) {
        dropAtBlock(location, data, 1);
    }

    public void dropAtBlock(Location location, T data, int amount) {
        ItemDelivery.dropAtBlock(location, create(data), amount);
    }

    public Optional<T> read(ItemStack item) {
        if (!matches(item)) return Optional.empty();
        return readData(item);
    }

    public boolean matches(ItemStack item) {
        return tags.getString(item, ITEM_ID_TAG)
                .map(id()::equals)
                .orElse(false);
    }

    public boolean interactInAir() {
        return false;
    }

    protected JavaPlugin plugin() {
        return plugin;
    }

    protected ItemTags tags() {
        return tags;
    }

    public void onClick(Player player, T data, InventoryClickEvent event) {
    }

    public void onInteract(Player player, T data, PlayerInteractEvent event) {
    }

    public void onPlace(Player player, T data, BlockPlaceEvent event) {
    }
}
