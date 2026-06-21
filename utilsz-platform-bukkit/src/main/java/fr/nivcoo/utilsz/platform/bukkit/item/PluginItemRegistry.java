package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unused")
public final class PluginItemRegistry implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, PluginItem<?>> items = new LinkedHashMap<>();
    private final Map<String, PluginBlock<?>> blocks = new LinkedHashMap<>();
    private boolean initialized;

    public PluginItemRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PluginItemRegistry register(PluginItem<?> item) {
        if (item == null) return this;
        items.put(item.id(), item);
        return this;
    }

    public PluginItemRegistry register(PluginBlock<?> block) {
        if (block == null) return this;
        blocks.put(block.id(), block);
        return this;
    }

    public void unregister(String id) {
        items.remove(id);
        blocks.remove(id);
    }

    public void init() {
        if (initialized) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        initialized = true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        dispatchClick(event.getCurrentItem(), player, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        dispatchInteract(event.getItem(), event.getPlayer(), event);
        if (!event.isCancelled()) dispatchBlockInteract(event.getClickedBlock(), event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        dispatchPlace(event.getItemInHand(), event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        dispatchBreak(event);
    }

    private void dispatchClick(ItemStack stack, Player player, InventoryClickEvent event) {
        if (stack == null || stack.getType().isAir()) return;
        for (PluginItem<?> item : items.values()) {
            if (dispatchClickOne(item, stack, player, event)) return;
        }
    }

    private <T> boolean dispatchClickOne(PluginItem<T> item, ItemStack stack, Player player, InventoryClickEvent event) {
        Optional<T> data = item.read(stack);
        if (data.isEmpty()) return false;
        item.onClick(player, data.get(), event);
        return true;
    }

    private void dispatchInteract(ItemStack stack, Player player, PlayerInteractEvent event) {
        if (stack == null || stack.getType().isAir()) return;
        for (PluginItem<?> item : items.values()) {
            if (dispatchInteractOne(item, stack, player, event)) return;
        }
    }

    private <T> boolean dispatchInteractOne(PluginItem<T> item, ItemStack stack, Player player, PlayerInteractEvent event) {
        Optional<T> data = item.read(stack);
        if (data.isEmpty()) return false;
        item.onInteract(player, data.get(), event);
        return true;
    }

    private void dispatchPlace(ItemStack stack, Player player, BlockPlaceEvent event) {
        if (stack == null || stack.getType().isAir()) return;
        for (PluginItem<?> item : items.values()) {
            if (dispatchPlaceOne(item, stack, player, event)) return;
        }
    }

    private <T> boolean dispatchPlaceOne(PluginItem<T> item, ItemStack stack, Player player, BlockPlaceEvent event) {
        Optional<T> data = item.read(stack);
        if (data.isEmpty()) return false;
        item.onPlace(player, data.get(), event);
        return true;
    }

    private void dispatchBreak(BlockBreakEvent event) {
        for (PluginBlock<?> block : blocks.values()) {
            if (dispatchBreakOne(block, event)) return;
        }
    }

    private void dispatchBlockInteract(Block clickedBlock, Player player, PlayerInteractEvent event) {
        if (clickedBlock == null) return;
        for (PluginBlock<?> block : blocks.values()) {
            if (dispatchBlockInteractOne(block, clickedBlock, player, event)) return;
        }
    }

    private <T> boolean dispatchBlockInteractOne(PluginBlock<T> block, Block clickedBlock, Player player, PlayerInteractEvent event) {
        Optional<T> data = block.read(clickedBlock);
        if (data.isEmpty()) return false;
        block.onInteract(player, data.get(), event);
        return true;
    }

    private <T> boolean dispatchBreakOne(PluginBlock<T> block, BlockBreakEvent event) {
        Optional<T> data = block.read(event.getBlock());
        if (data.isEmpty()) return false;
        block.onBreak(event.getPlayer(), data.get(), event);
        return true;
    }
}
