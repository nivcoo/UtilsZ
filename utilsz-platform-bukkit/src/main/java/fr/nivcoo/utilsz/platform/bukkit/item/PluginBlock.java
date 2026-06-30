package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

@SuppressWarnings("unused")
public abstract class PluginBlock<T> {

    private final JavaPlugin plugin;

    protected PluginBlock(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Optional<T> read(Block block);

    protected JavaPlugin plugin() {
        return plugin;
    }

    public void onPlace(Player player, T data, BlockPlaceEvent event) {
    }

    public boolean shouldPassThroughInteract(Player player, T data, PlayerInteractEvent event) {
        return PluginBlockGuards.shouldPassThroughPlaceableInteract(event);
    }

    public boolean shouldPreventMergedChest(Player player, T data, BlockPlaceEvent event) {
        return true;
    }

    public void onMergedChestPrevented(Player player, T data, BlockPlaceEvent event) {
    }

    public boolean shouldPreventAdjacentTouch(Player player, T data, BlockPlaceEvent event) {
        return false;
    }

    public void onAdjacentTouchPrevented(Player player, T data, BlockPlaceEvent event) {
    }

    public void onInteract(Player player, T data, PlayerInteractEvent event) {
    }

    public void onBreak(Player player, T data, BlockBreakEvent event) {
    }
}
