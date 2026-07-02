package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
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

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean air = action == Action.RIGHT_CLICK_AIR || action == Action.LEFT_CLICK_AIR;
        if (air) {
            dispatchInteract(event.getItem(), event.getPlayer(), event, true);
            return;
        }

        if (event.isCancelled()) return;
        dispatchInteract(event.getItem(), event.getPlayer(), event, false);
        if (!event.isCancelled()) dispatchBlockInteract(event.getClickedBlock(), event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        dispatchPlace(event.getItemInHand(), event.getPlayer(), event);
        if (!event.isCancelled()) dispatchAdjacentPlaceGuards(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        dispatchBreak(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (containsPistonProtectedBlock(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (containsPistonProtectedBlock(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block == null || !block.getType().hasGravity()) return;
        if (shouldPreventPhysicsFall(block, event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (shouldPreventEntityChange(event.getBlock(), event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (shouldPreventFlowReplace(event.getToBlock(), event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (shouldPreventFade(event.getBlock(), event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (shouldPreventBurn(event.getBlock(), event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (shouldPreventLeavesDecay(event.getBlock(), event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        dispatchExplosion(event.blockList(), new PluginBlockDestroyContext(null, PluginBlockDestroyCause.EXPLOSION, event));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        dispatchExplosion(event.blockList(), new PluginBlockDestroyContext(null, PluginBlockDestroyCause.EXPLOSION, event));
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

    private void dispatchInteract(ItemStack stack, Player player, PlayerInteractEvent event, boolean air) {
        if (stack == null || stack.getType().isAir()) return;
        for (PluginItem<?> item : items.values()) {
            if (dispatchInteractOne(item, stack, player, event, air)) return;
        }
    }

    private <T> boolean dispatchInteractOne(PluginItem<T> item, ItemStack stack, Player player, PlayerInteractEvent event, boolean air) {
        if (air && !item.interactInAir()) return false;
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
        T value = data.get();
        if (block.shouldPassThroughInteract(player, value, event)) return true;
        block.onInteract(player, value, event);
        return true;
    }

    private <T> boolean dispatchBreakOne(PluginBlock<T> block, BlockBreakEvent event) {
        Optional<T> data = block.read(event.getBlock());
        if (data.isEmpty()) return false;
        block.onBreak(event.getPlayer(), data.get(), event);
        return true;
    }

    private void dispatchAdjacentPlaceGuards(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed == null) return;
        for (BlockFace face : PluginBlockGuards.HORIZONTAL_FACES) {
            Block adjacent = placed.getRelative(face);
            for (PluginBlock<?> block : blocks.values()) {
                if (dispatchAdjacentPlaceGuardOne(block, adjacent, event)) return;
            }
        }
    }

    private <T> boolean dispatchAdjacentPlaceGuardOne(PluginBlock<T> block, Block adjacent, BlockPlaceEvent event) {
        Optional<T> data = block.read(adjacent);
        if (data.isEmpty()) return false;
        T value = data.get();
        Player player = event.getPlayer();
        if (PluginBlockGuards.createsMergedChest(event.getBlockPlaced())
                && block.shouldPreventMergedChest(player, value, event)) {
            event.setCancelled(true);
            block.onMergedChestPrevented(player, value, event);
            return true;
        }
        if (block.shouldPreventAdjacentTouch(player, value, event)) {
            event.setCancelled(true);
            block.onAdjacentTouchPrevented(player, value, event);
            return true;
        }
        return false;
    }

    private boolean containsPistonProtectedBlock(Iterable<Block> movedBlocks) {
        for (Block movedBlock : movedBlocks) {
            for (PluginBlock<?> block : blocks.values()) {
                if (isPistonProtectedOne(block, movedBlock)) return true;
            }
        }
        return false;
    }

    private <T> boolean isPistonProtectedOne(PluginBlock<T> block, Block movedBlock) {
        Optional<T> data = block.read(movedBlock);
        return data.isPresent() && block.shouldPreventPistonMove(movedBlock, data.get());
    }

    private boolean shouldPreventPhysicsFall(Block target, BlockPhysicsEvent event) {
        for (PluginBlock<?> block : blocks.values()) {
            if (shouldPreventPhysicsFallOne(block, target, event)) return true;
        }
        return false;
    }

    private <T> boolean shouldPreventPhysicsFallOne(PluginBlock<T> block, Block target, BlockPhysicsEvent event) {
        Optional<T> data = block.read(target);
        return data.isPresent() && block.shouldPreventPhysicsFall(target, data.get(), event);
    }

    private boolean shouldPreventEntityChange(Block target, EntityChangeBlockEvent event) {
        for (PluginBlock<?> block : blocks.values()) {
            if (shouldPreventEntityChangeOne(block, target, event)) return true;
        }
        return false;
    }

    private <T> boolean shouldPreventEntityChangeOne(PluginBlock<T> block, Block target, EntityChangeBlockEvent event) {
        Optional<T> data = block.read(target);
        return data.isPresent() && block.shouldPreventEntityChange(target, data.get(), event);
    }

    private boolean shouldPreventFlowReplace(Block target, BlockFromToEvent event) {
        for (PluginBlock<?> block : blocks.values()) {
            if (shouldPreventFlowReplaceOne(block, target, event)) return true;
        }
        return false;
    }

    private <T> boolean shouldPreventFlowReplaceOne(PluginBlock<T> block, Block target, BlockFromToEvent event) {
        Optional<T> data = block.read(target);
        return data.isPresent() && block.shouldPreventFlowReplace(target, data.get(), event);
    }

    private boolean shouldPreventFade(Block target, BlockFadeEvent event) {
        for (PluginBlock<?> block : blocks.values()) {
            if (shouldPreventFadeOne(block, target, event)) return true;
        }
        return false;
    }

    private <T> boolean shouldPreventFadeOne(PluginBlock<T> block, Block target, BlockFadeEvent event) {
        Optional<T> data = block.read(target);
        return data.isPresent() && block.shouldPreventFade(target, data.get(), event);
    }

    private boolean shouldPreventBurn(Block target, BlockBurnEvent event) {
        for (PluginBlock<?> block : blocks.values()) {
            if (shouldPreventBurnOne(block, target, event)) return true;
        }
        return false;
    }

    private <T> boolean shouldPreventBurnOne(PluginBlock<T> block, Block target, BlockBurnEvent event) {
        Optional<T> data = block.read(target);
        return data.isPresent() && block.shouldPreventBurn(target, data.get(), event);
    }

    private boolean shouldPreventLeavesDecay(Block target, LeavesDecayEvent event) {
        for (PluginBlock<?> block : blocks.values()) {
            if (shouldPreventLeavesDecayOne(block, target, event)) return true;
        }
        return false;
    }

    private <T> boolean shouldPreventLeavesDecayOne(PluginBlock<T> block, Block target, LeavesDecayEvent event) {
        Optional<T> data = block.read(target);
        return data.isPresent() && block.shouldPreventLeavesDecay(target, data.get(), event);
    }

    private void dispatchExplosion(List<Block> explodedBlocks, PluginBlockDestroyContext baseContext) {
        for (Block explodedBlock : List.copyOf(explodedBlocks)) {
            if (destroyBlock(explodedBlock, baseContext)) explodedBlocks.remove(explodedBlock);
        }
    }

    private boolean destroyBlock(Block target, PluginBlockDestroyContext baseContext) {
        if (target == null) return false;
        for (PluginBlock<?> block : blocks.values()) {
            if (destroyBlockOne(block, target, baseContext)) return true;
        }
        return false;
    }

    private <T> boolean destroyBlockOne(PluginBlock<T> block, Block target, PluginBlockDestroyContext baseContext) {
        Optional<T> data = block.read(target);
        if (data.isEmpty()) return false;
        T value = data.get();
        PluginBlockDestroyContext context = new PluginBlockDestroyContext(target, baseContext.cause(), baseContext.event());
        if (!block.shouldDestroy(value, context)) return true;
        block.onDestroy(value, context);
        if (!target.getType().isAir()) target.setType(Material.AIR, false);
        return true;
    }
}
