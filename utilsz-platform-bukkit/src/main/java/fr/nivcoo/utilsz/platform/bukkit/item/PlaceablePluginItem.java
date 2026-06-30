package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public abstract class PlaceablePluginItem<T, B extends PluginBlock<T>> extends PluginItem<T> {

    private final B block;

    protected PlaceablePluginItem(JavaPlugin plugin, B block) {
        super(plugin);
        if (block == null) throw new IllegalArgumentException("PlaceablePluginItem block cannot be null.");
        this.block = block;
    }

    public final B block() {
        return block;
    }

    @Override
    public void onPlace(Player player, T data, BlockPlaceEvent event) {
        if (block.shouldPreventMergedChest(player, data, event) && PluginBlockGuards.createsMergedChest(event.getBlockPlaced())) {
            event.setCancelled(true);
            block.onMergedChestPrevented(player, data, event);
            return;
        }
        block.onPlace(player, data, event);
    }
}
