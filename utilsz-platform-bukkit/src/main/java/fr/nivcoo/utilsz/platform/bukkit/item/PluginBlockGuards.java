package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Predicate;

@SuppressWarnings("unused")
public final class PluginBlockGuards {

    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };

    private PluginBlockGuards() {
    }

    public static boolean isPlaceable(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        Material material = item.getType();
        return material.isBlock();
    }

    public static boolean shouldPassThroughPlaceableInteract(PlayerInteractEvent event) {
        return event != null && event.getPlayer().isSneaking() && isPlaceable(event.getItem());
    }

    public static boolean createsMergedChest(Block block) {
        return block != null && block.getBlockData() instanceof Chest chest && chest.getType() != Chest.Type.SINGLE;
    }

    public static boolean touchesAdjacent(Block block, Predicate<Block> predicate) {
        if (block == null || predicate == null) return false;
        for (BlockFace face : HORIZONTAL) {
            if (predicate.test(block.getRelative(face))) return true;
        }
        return false;
    }
}
