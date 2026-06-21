package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

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

    public void give(Player player, T data) {
        ItemDelivery.giveOrDrop(player, create(data));
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
