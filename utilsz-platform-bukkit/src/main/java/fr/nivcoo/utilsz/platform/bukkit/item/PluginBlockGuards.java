package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("unused")
public final class PluginBlockGuards {

    public static final BlockFace[] HORIZONTAL_FACES = {
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
}
